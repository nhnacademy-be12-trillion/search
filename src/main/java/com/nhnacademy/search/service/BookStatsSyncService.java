package com.nhnacademy.search.service;

import com.nhnacademy.search.config.BookStatsSyncProperties;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.es.EsBookStatsUpdater;
import com.nhnacademy.search.repository.BookStatsReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookStatsSyncService {

    private final BookStatsReadRepository repo;
    private final EsBookStatsUpdater esUpdater;
    private final ElasticsearchProperties esProps;
    private final BookStatsSyncProperties props;

    public void runFullSync() {
        String indexName = esProps.getIndex().getBook();

        long lastBookId = 0L;
        long totalBooks = 0L;
        long updatedIsbn = 0L;

        while (true) {
            List<BookStatsReadRepository.BookRow> page = repo.findBookPage(lastBookId, props.getPageSize());
            if (page.isEmpty()) break;

            lastBookId = page.get(page.size() - 1).bookId();
            totalBooks += page.size();

            List<Long> bookIds = page.stream().map(BookStatsReadRepository.BookRow::bookId).toList();
            Map<Long, BookStatsReadRepository.ReviewAgg> aggMap = repo.findReviewAggByBookIds(bookIds);

            Map<String, EsBookStatsUpdater.BookStats> isbnToStats = new LinkedHashMap<>();

            for (var b : page) {
                String isbn = (b.isbn() == null) ? null : b.isbn().trim();
                if (isbn == null || isbn.isEmpty()) continue;

                var agg = aggMap.get(b.bookId());
                int reviewCount = (agg == null) ? 0 : agg.reviewCount();
                int ratingCount = (agg == null) ? 0 : agg.ratingCount();
                Double ratingAvg = (agg == null) ? null : agg.ratingAvg();

                // popularityScore 임시로 viewCount 사용 -> 나중에 수정
                double popularityScore = (double) b.viewCount();

                isbnToStats.put(isbn, new EsBookStatsUpdater.BookStats(
                        reviewCount, ratingCount, ratingAvg, popularityScore
                ));
            }

            if (!isbnToStats.isEmpty()) {
                esUpdater.updateBookStatsByIsbn(indexName, isbnToStats, props.getUbqChunkSize());
                updatedIsbn += isbnToStats.size();
            }

            log.info("[BookStatsSync] progress lastBookId={} pageBooks={} totalBooks={} updatedIsbn={}",
                    lastBookId, page.size(), totalBooks, updatedIsbn);
        }

        log.info("[BookStatsSync] DONE totalBooks={} updatedIsbn={}", totalBooks, updatedIsbn);
    }
}
