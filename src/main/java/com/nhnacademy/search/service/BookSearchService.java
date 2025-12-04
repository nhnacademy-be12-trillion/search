package com.nhnacademy.search.service;

import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import com.nhnacademy.search.dto.BookSearchResult;
import com.nhnacademy.search.dto.BookSortOption;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookSearchService {

    private final WebClient elasticsearchWebClient;
    private final ElasticsearchProperties properties;

    public BookSearchResponse search(BookSearchRequest request) {
        // page/size 정규화는 한 번만 여기서 수행
        int page = Math.max(request.page(), 0);
        int size = normalizeSize(request.size());

        Map<String, Object> esQuery = buildEsQuery(request, page, size);

        Map<String, Object> esResponse = elasticsearchWebClient.post()
                .uri("/" + properties.getIndex().getBook() + "/_search")
                .bodyValue(esQuery)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        return mapToResponse(esResponse, page, size);
    }

    private Map<String, Object> buildEsQuery(BookSearchRequest req, int page, int size) {
        Map<String, Object> root = new HashMap<>();

        // _source 필터링
        Map<String, Object> source = Map.of(
                "includes", List.of(
                        "id",
                        "metadata.isbn",
                        "metadata.title",
                        "metadata.author",
                        "metadata.publisher",
                        "metadata.price",
                        "metadata.imageUrl",
                        "metadata.editionPublishDate"
                )
        );
        root.put("_source", source);

        int from = page * size;

        root.put("from", from);
        root.put("size", size);

        // multi_match, 가중치 설정
        Map<String, Object> multiMatch = new HashMap<>();
        multiMatch.put("query", req.query());
        multiMatch.put("type", "best_fields");
        multiMatch.put("operator", "and");
        multiMatch.put("fields", List.of(
                "metadata.title^100",
                "title^100",
                "metadata.author^90",
                "authorName^90",
                "metadata.tags^80",
                "isbn^70",
                "metadata.isbn^70",
                "metadata.publisher^60",
                "publisherName^60",
                "metadata.content^50",
                "bookContent^50",
                "metadata.reviewSummary^40"
        ));

        // null 방어
        String q = req.query();
        if (q == null || q.isBlank()) {
            root.put("query", Map.of("match_all", Map.of()));
        } else {
            root.put("query", Map.of("multi_match", multiMatch));
        }

        // sort
        List<Object> sort = buildSort(req.sort());
        if (!sort.isEmpty()) {
            root.put("sort", sort);
        }

        return root;
    }

    // size 최소/최대값 제한
    private int normalizeSize(int size) {
        int defaultSize = 10;
        int maxSize = 50;
        if (size <= 0) {
            return defaultSize;
        }
        return Math.min(size, maxSize);
    }

    private List<Object> buildSort(BookSortOption sort) {
        if (sort == null || sort == BookSortOption.RELEVANCE) {
            return List.of(); // ES 기본 _score 정렬
        }

        String field;
        String order;

        switch (sort) {
            case NEW -> {
                field = "metadata.editionPublishDate";
                order = "desc";
            }
            case LOW_PRICE -> {
                field = "metadata.price";
                order = "asc";
            }
            case HIGH_PRICE -> {
                field = "metadata.price";
                order = "desc";
            }
            case POPULARITY -> {
                field = "popularityScore";
                order = "desc";
            }
            case RATING -> {
                field = "ratingAvg";
                order = "desc";
            }
            case REVIEW_COUNT -> {
                field = "reviewCount";
                order = "desc";
            }
            default -> {
                return List.of();
            }
        }

        // 가격/날짜 없는 문서는 뒤로 밀기
        Map<String, Object> fieldSort = new HashMap<>();
        fieldSort.put("order", order);
        fieldSort.put("missing", "_last");   // null(없는 값)은 항상 맨 뒤

        // 같은 가격/날짜 안에서는 score 높은 순
        Map<String, Object> scoreSort = Map.of(
                "_score", Map.of("order", "desc")
        );

        return List.of(
                Map.of(field, fieldSort),  // 1순위: 가격 or 날짜
                scoreSort                  // 2순위: score
        );
    }

    @SuppressWarnings("unchecked")
    private BookSearchResponse mapToResponse(Map<String, Object> esResponse, int page, int size) {
        // esResponse null 방어
        if (esResponse == null) {
            return new BookSearchResponse(List.of(), 0L, page, size);
        }

        Map<String, Object> hitsRoot = (Map<String, Object>) esResponse.get("hits");
        if (hitsRoot == null) {
            return new BookSearchResponse(List.of(), 0L, page, size);
        }

        Map<String, Object> totalObj = (Map<String, Object>) hitsRoot.get("total");
        long total = 0L;
        if (totalObj != null) {
            Object value = totalObj.get("value");
            if (value instanceof Number number) {
                total = number.longValue();
            }
        }

        List<Map<String, Object>> hitList = (List<Map<String, Object>>) hitsRoot.get("hits");
        if (hitList == null) {
            return new BookSearchResponse(List.of(), total, page, size);
        }

        List<BookSearchResult> results = hitList.stream()
                .map(hit -> {
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    if (source == null) {
                        return null;
                    }

                    Map<String, Object> metadata =
                            (Map<String, Object>) source.getOrDefault("metadata", Map.of());

                    // id 타입 방어 (String/Number 모두 허용)
                    String id = source.get("id") != null ? String.valueOf(source.get("id")) : null;

                    String isbn = asString(metadata.get("isbn"));
                    String title = asString(metadata.get("title"));
                    String author = asString(metadata.get("author"));
                    String publisher = asString(metadata.get("publisher"));

                    // 가격이 없는 책에 대하여 null 허용
                    Integer price = asInteger(metadata.get("price"));
                    String imageUrl = asString(metadata.get("imageUrl"));
                    String editionDate = asString(metadata.get("editionPublishDate"));

                    // score 도 price 와 동일하게 null 허용
                    Float score = null;
                    Object scoreRaw = hit.get("_score");
                    if (scoreRaw instanceof Number number) {
                        score = number.floatValue();
                    }

                    return new BookSearchResult(
                            id, isbn, title, author, publisher,
                            price, imageUrl, editionDate, score
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        return new BookSearchResponse(results, total, page, size);
    }

    private String asString(Object value) {
        return (value != null) ? String.valueOf(value) : null;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
