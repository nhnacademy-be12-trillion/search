// search-server
package com.nhnacademy.search.service;

import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.repository.BookAuthorReadRepository;
import com.nhnacademy.search.repository.BookAuthorReadRepository.BookAuthorsRow;
import com.nhnacademy.search.repository.BookIndexingReadRepository;
import com.nhnacademy.search.repository.BookIndexingReadRepository.BookIndexingRow;
import com.nhnacademy.search.repository.BookStatsReadRepository;
import com.nhnacademy.search.repository.BookTagReadRepository;
import com.nhnacademy.search.repository.BookTagReadRepository.BookTagsRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookSingleIndexService {

    private final BookIndexingReadRepository bookIndexingReadRepository;
    private final BookAuthorReadRepository bookAuthorReadRepository;
    private final BookTagReadRepository bookTagReadRepository;
    private final BookStatsReadRepository bookStatsReadRepository;

    private final ElasticsearchProperties elasticsearchProperties;

    @Qualifier("elasticsearchWebClient")
    private final WebClient esWebClient;

    // 신규: isbn -> DB조회(재시도) -> ES upsert
    public void upsertByIsbnWithRetry(String rawIsbn) {
        String indexName = requireIndexName();
        String isbn = normalizeIsbn(rawIsbn);

        int maxAttempts = 8;
        long delayMs = 120;

        Optional<BookIndexingRow> found = Optional.empty();
        int attempts = 0;

        for (int i = 1; i <= maxAttempts; i++) {
            attempts = i;
            found = bookIndexingReadRepository.findByIsbn(isbn);
            if (found.isPresent()) break;

            if (i < maxAttempts) {
                sleep(delayMs);
                delayMs = Math.min((long) (delayMs * 1.7), 1500);
            }
        }

        BookIndexingRow base = found.orElseThrow(
                () -> new NotFoundException("book not ready (not found by isbn after retry). isbn=" + isbn)
        );

        long bookId = base.bookId();
        Map<String, Object> doc = buildDocument(base);

        putDoc(indexName, bookId, doc);
        log.info("[SingleIndexService] upsertByIsbn done. isbn={} bookId={} attempts={}", isbn, bookId, attempts);
    }

    // 업데이트: bookId -> DB조회 -> ES upsert
    public void upsertByBookId(long bookId) {
        String indexName = requireIndexName();

        BookIndexingRow base = bookIndexingReadRepository.findByBookId(bookId)
                .orElseThrow(() -> new NotFoundException("book not found. bookId=" + bookId));

        Map<String, Object> doc = buildDocument(base);
        putDoc(indexName, bookId, doc);

        log.info("[SingleIndexService] upsertByBookId done. bookId={} isbn={}", bookId, base.isbn());
    }

    // 삭제: bookId -> ES delete (없어도 성공)
    public void deleteByBookId(long bookId) {
        String indexName = requireIndexName();

        try {
            esWebClient.delete()
                    .uri("/{index}/_doc/{id}", indexName, bookId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("[SingleIndexService] deleted ES doc. bookId={}", bookId);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("[SingleIndexService] ES doc already absent. bookId={}", bookId);
                return;
            }
            throw e;
        }
    }

    // 내부: 도큐먼트 구성 (embeddingVector 제외 / 나머지 채움)
    private Map<String, Object> buildDocument(BookIndexingRow base) {
        long bookId = base.bookId();

        Map<Long, BookAuthorsRow> authorsMap = bookAuthorReadRepository.findByBookIds(List.of(bookId));
        BookAuthorsRow authorsRow = authorsMap.get(bookId);

        Map<Long, BookTagsRow> tagsMap = bookTagReadRepository.findByBookIds(List.of(bookId));
        BookTagsRow tagsRow = tagsMap.get(bookId);

        Map<Long, BookStatsReadRepository.ReviewAgg> aggMap = bookStatsReadRepository.findReviewAggByBookIds(List.of(bookId));
        BookStatsReadRepository.ReviewAgg agg = aggMap.get(bookId);

        long viewCount = bookStatsReadRepository.findViewCountByBookId(bookId);

        return toDocument(base, authorsRow, tagsRow, agg, viewCount);
    }

    private Map<String, Object> toDocument(BookIndexingRow row,
                                           BookAuthorsRow authorsRow,
                                           BookTagsRow tagsRow,
                                           BookStatsReadRepository.ReviewAgg agg,
                                           long viewCount) {

        String content = row.bookDescription();
        String authors = (authorsRow != null ? authorsRow.authorsCsv() : null);

        List<String> tags = parseTags(tagsRow != null ? tagsRow.tagsCsv() : null);

        int reviewCount = (agg != null ? agg.reviewCount() : 0);
        int ratingCount = (agg != null ? agg.ratingCount() : 0);
        double ratingAvg = (agg != null && agg.ratingAvg() != null ? agg.ratingAvg() : 0.0);

        float popularityScore = (float) viewCount;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("isbn", row.isbn());
        metadata.put("title", row.bookName());
        metadata.put("publisher", row.publisherName());
        metadata.put("price", row.bookRegularPrice());
        metadata.put("salePrice", row.bookSalePrice());
        if (row.imageUrl() != null && !row.imageUrl().isBlank()) {
            metadata.put("imageUrl", row.imageUrl());
        }
        metadata.put("editionPublishDate", row.bookPublicationDate());
        metadata.put("content", content);
        metadata.put("reviewSummary", row.bookReviewSummary());
        metadata.put("tags", tags);

        if (authors != null && !authors.isBlank()) {
            metadata.put("author", authors);
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", String.valueOf(row.bookId()));
        doc.put("isbn", row.isbn());
        doc.put("title", row.bookName());
        doc.put("publisherName", row.publisherName());
        doc.put("bookContent", content);

        if (authors != null && !authors.isBlank()) {
            doc.put("authorName", authors);
        }

        doc.put("reviewCount", reviewCount);
        doc.put("ratingCount", ratingCount);
        doc.put("ratingAvg", (float) ratingAvg);
        doc.put("popularityScore", popularityScore);

        doc.put("metadata", metadata);

        // embeddingVector는 일부러 안 넣음
        return doc;
    }

    private List<String> parseTags(String tagsCsv) {
        if (tagsCsv == null || tagsCsv.isBlank()) return List.of();

        return Arrays.stream(tagsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private void putDoc(String indexName, long bookId, Map<String, Object> doc) {
        String resp = esWebClient.put()
                .uri("/{index}/_doc/{id}", indexName, bookId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(doc)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("[SingleIndexService] upserted doc. index={} bookId={} resp={}", indexName, bookId, resp);
    }

    private String requireIndexName() {
        String indexName = elasticsearchProperties.getIndex().getBook();
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalStateException("elasticsearch.index.book 이 설정되어 있지 않습니다.");
        }
        return indexName;
    }

    private String normalizeIsbn(String s) {
        if (s == null) return null;
        return s.trim().replace("-", "");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }
}
