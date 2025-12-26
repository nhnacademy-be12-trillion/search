package com.nhnacademy.search.service;

import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.es.EsReviewSummaryUpdater;
import com.nhnacademy.search.client.GeminiClient;
import com.nhnacademy.search.repository.BookIsbnReadRepository;
import com.nhnacademy.search.repository.BookWriteRepository;
import com.nhnacademy.search.repository.ReviewReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSummaryPipelineService {

    private final ReviewReadRepository reviewRepo;
    private final BookIsbnReadRepository bookRepo;
    private final BookWriteRepository bookWriteRepo;
    private final GeminiClient geminiClient;
    private final EsReviewSummaryUpdater esUpdater;
    private final ElasticsearchProperties esProps;

    public void runOnce(int recentReviewLimit, int perBookReviewLimit) {
        List<Long> bookIds = reviewRepo.findRecentReviewBookIds(recentReviewLimit);

        if (bookIds.isEmpty()) {
            log.info("[ReviewSummaryPipeline] 최근 리뷰가 없어 종료");
            return;
        }

        Map<String, String> isbnToSummary = new LinkedHashMap<>();

        for (long bookId : bookIds) {
            int totalReviews = reviewRepo.countAllReviewContentsByBookId(bookId);
            if (totalReviews < 10) continue;

            List<String> reviews = reviewRepo.findRecentReviewContentsByBookId(bookId, perBookReviewLimit);
            if (reviews.isEmpty()) continue;

            var isbnOpt = bookRepo.findIsbnByBookId(bookId);
            if (isbnOpt.isEmpty()) continue;

            String isbn = isbnOpt.get();
            String prompt = buildPrompt(reviews);

            String summary = geminiClient.generateText(prompt);
            if (summary == null || summary.isBlank()) continue;

            // DB 저장
            try {
                bookWriteRepo.updateReviewSummary(bookId, summary.trim());
            } catch (Exception e) {
                log.error("[ReviewSummaryPipeline] DB update failed. bookId={}", bookId, e);
            }

            // ES 저장 데이터 수집
            isbnToSummary.put(isbn, summary.trim());
        }

        if (isbnToSummary.isEmpty()) {
            log.info("[ReviewSummaryPipeline] 업데이트할 요약이 없어 종료");
            return;
        }

        // ES 저장
        String indexName = esProps.getIndex().getBook();
        esUpdater.updateReviewSummaryByIsbn(indexName, isbnToSummary);
        log.info("[ReviewSummaryPipeline] done. books={} updatedIsbn={}", bookIds.size(), isbnToSummary.size());
    }

    private String buildPrompt(List<String> reviews) {
        String joined = String.join("\n- ", reviews.stream().limit(50).toList());
        return """
               너는 한국어 리뷰 요약 전문가야.
               아래는 한 책에 대한 사용자 리뷰 목록이야.

               요구사항:
               - 3~5문장으로 간결하게
               - 장점/단점 모두 포함
               - 과도한 추측 금지, 리뷰에 없는 내용 만들지 말 것
               - 스포일러 금지

               리뷰:
               - %s
               """.formatted(joined);
    }
}
