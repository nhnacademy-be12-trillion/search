package com.nhnacademy.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.repository.BookAuthorReadRepository;
import com.nhnacademy.search.repository.BookAuthorReadRepository.BookAuthorsRow;
import com.nhnacademy.search.repository.BookIndexingReadRepository;
import com.nhnacademy.search.repository.BookIndexingReadRepository.BookIndexingRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookFullReindexService {

    private static final int BASE_PAGE_SIZE = 500;   // 책 베이스 인덱싱용
    private static final int TAG_PAGE_SIZE  = 500;   // 태그 인덱싱용

    private final BookIndexingReadRepository bookIndexingReadRepository;
    private final BookAuthorReadRepository bookAuthorReadRepository;  // 🔹 추가
    private final TagIndexService tagIndexService;
    private final BookStatsSyncService bookStatsSyncService;
    private final ElasticsearchProperties elasticsearchProperties;
    @Qualifier("elasticsearchWebClient")
    private final WebClient esWebClient;
    private final ObjectMapper objectMapper;

    /**
     * 전체 풀 리인덱스 엔트리 포인트
     *
     *  1) Book / Publisher / BookFile 기준 베이스 도큐먼트 인덱싱
     *  2) 태그 동기화 (metadata.tags)
     *  3) 리뷰/조회수 통계 동기화 (reviewCount / ratingCount / ratingAvg / popularityScore)
     */
    public void runFullReindex() {
        String indexName = elasticsearchProperties.getIndex().getBook();
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalStateException("elasticsearch.index.book 이 설정되어 있지 않습니다.");
        }

        log.info("[FullReindexService] START full reindex. index={}", indexName);

        reindexBaseBooks(indexName, BASE_PAGE_SIZE);             // 1) 베이스 + 저자
        tagIndexService.syncTagsToEs(indexName, TAG_PAGE_SIZE);  // 2) 태그
        bookStatsSyncService.runFullSync();                      // 3) 리뷰/평점/인기점수

        log.info("[FullReindexService] END full reindex. index={}", indexName);
    }

    /**
     * BookIndexingReadRepository + BookAuthorReadRepository 기반으로
     * 베이스 도큐먼트를 ES에 인덱싱한다.
     */
    void reindexBaseBooks(String indexName, int pageSize) {
        long lastBookId = 0L;

        while (true) {
            List<BookIndexingRow> rows =
                    bookIndexingReadRepository.findBooksAfterId(lastBookId, pageSize);

            if (rows.isEmpty()) {
                break;
            }

            // 이번 batch 의 bookId 들
            List<Long> bookIds = rows.stream()
                    .map(BookIndexingRow::bookId)
                    .toList();

            // bookId -> BookAuthorsRow (isbn, authorsCsv)
            Map<Long, BookAuthorsRow> authorsMap =
                    bookAuthorReadRepository.findByBookIds(bookIds);

            bulkIndex(indexName, rows, authorsMap);

            lastBookId = rows.get(rows.size() - 1).bookId();
        }
    }

    /**
     * ES _bulk API 호출 (application/x-ndjson)
     */
    private void bulkIndex(String indexName,
                           List<BookIndexingRow> rows,
                           Map<Long, BookAuthorsRow> authorsMap) {

        if (rows.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        for (BookIndexingRow row : rows) {
            BookAuthorsRow authorsRow = authorsMap.get(row.bookId());
            Map<String, Object> doc = toDocument(row, authorsRow);

            // action line
            sb.append("{\"index\":")
                    .append("{\"_index\":\"")
                    .append(indexName)
                    .append("\",\"_id\":\"")
                    .append(row.bookId())
                    .append("\"}}\n");

            // source line
            try {
                sb.append(objectMapper.writeValueAsString(doc)).append("\n");
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize bookId=" + row.bookId(), e);
            }
        }

        String ndjson = sb.toString();

        String resp = esWebClient.post()
                .uri("/_bulk")
                .contentType(MediaType.APPLICATION_NDJSON)
                .bodyValue(ndjson)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("[FullReindexService] bulk indexed {} docs. resp={}", rows.size(), resp);
    }

    /**
     * BookIndexingRow + BookAuthorsRow -> ES 도큐먼트(Map) 변환
     *
     * - top-level: id, isbn, title, publisherName, bookContent, authorName
     * - metadata : isbn, title, publisher, price, salePrice, imageUrl,
     *              editionPublishDate, content, reviewSummary, author
     *
     *  -> trillion_books_dev 매핑에 맞춰서 채우기.
     */
    private Map<String, Object> toDocument(BookIndexingRow row,
                                           BookAuthorsRow authorsRow) {
        String content = row.bookDescription();
        String authors = (authorsRow != null ? authorsRow.authorsCsv() : null);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("isbn", row.isbn());
        metadata.put("title", row.bookName());
        metadata.put("publisher", row.publisherName());
        metadata.put("price", row.bookRegularPrice());
        metadata.put("salePrice", row.bookSalePrice());
        metadata.put("imageUrl", row.imageUrl());
        metadata.put("editionPublishDate", row.bookPublicationDate());
        metadata.put("content", content);
        metadata.put("reviewSummary", row.bookReviewSummary());

        // metadata.author 채우기
        if (authors != null && !authors.isBlank()) {
            metadata.put("author", authors);
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", String.valueOf(row.bookId()));
        doc.put("isbn", row.isbn());
        doc.put("title", row.bookName());
        doc.put("publisherName", row.publisherName());
        doc.put("bookContent", content);

        // top-level authorName 채우기
        if (authors != null && !authors.isBlank()) {
            doc.put("authorName", authors);
        }

        doc.put("metadata", metadata);
        // ratingAvg, ratingCount, reviewCount, popularityScore, tags, embeddingVector 등은
        //  - bookStatsSyncService / TagIndexService / (추후 EmbeddingService) 에서 채움

        return doc;
    }
}
