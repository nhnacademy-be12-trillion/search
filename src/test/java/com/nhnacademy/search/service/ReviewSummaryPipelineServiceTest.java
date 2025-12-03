package com.nhnacademy.search.service;

import com.nhnacademy.search.client.GeminiClient;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.es.EsReviewSummaryUpdater;
import com.nhnacademy.search.repository.BookIsbnReadRepository;
import com.nhnacademy.search.repository.ReviewReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewSummaryPipelineServiceTest {

    @Mock ReviewReadRepository reviewRepo;
    @Mock BookIsbnReadRepository bookRepo;
    @Mock GeminiClient geminiClient;
    @Mock EsReviewSummaryUpdater esUpdater;
    @Mock ElasticsearchProperties esProps;
    @Mock ElasticsearchProperties.Index indexProps;

    ReviewSummaryPipelineService service;

    @BeforeEach
    void setUp() {
        service = new ReviewSummaryPipelineService(reviewRepo, bookRepo, geminiClient, esUpdater, esProps);
    }

    @Test
    void runOnce_updatesEs_whenReviewsExist() {
        // given
        when(reviewRepo.findRecentReviewBookIds(50)).thenReturn(List.of(10L, 20L));

        when(reviewRepo.findRecentReviewContentsByBookId(10L, 30))
                .thenReturn(List.of("재밌어요", "구성이 좋아요"));
        when(reviewRepo.findRecentReviewContentsByBookId(20L, 30))
                .thenReturn(List.of()); // skip

        when(bookRepo.findIsbnByBookId(10L)).thenReturn(Optional.of("9780000000001"));

        when(geminiClient.generateText(anyString()))
                .thenReturn("장점과 단점을 모두 포함한 3~5문장 요약");

        when(esProps.getIndex()).thenReturn(indexProps);
        when(indexProps.getBook()).thenReturn("trillion_books");

        // when
        service.runOnce(50, 30);

        // then -> ES 업데이트 호출 + map 내용 검증
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass((Class) Map.class);

        verify(esUpdater, times(1))
                .updateReviewSummaryByIsbn(eq("trillion_books"), captor.capture());

        Map<String, String> isbnToSummary = captor.getValue();
        assertEquals(1, isbnToSummary.size());
        assertEquals("장점과 단점을 모두 포함한 3~5문장 요약", isbnToSummary.get("9780000000001"));

        // bookId=20은 리뷰가 없어 ISBN 조회 안 해야 함
        verify(bookRepo, never()).findIsbnByBookId(20L);

        verify(geminiClient, times(1)).generateText(anyString());

        verify(reviewRepo, times(1)).findRecentReviewContentsByBookId(10L, 30);
        verify(reviewRepo, times(1)).findRecentReviewContentsByBookId(20L, 30);

        verifyNoMoreInteractions(esUpdater);
    }

    @Test
    void runOnce_doesNothing_whenNoRecentReviews() {
        // given
        when(reviewRepo.findRecentReviewBookIds(50)).thenReturn(List.of());

        // when
        service.runOnce(50, 30);

        // then -> 조기 종료이므로 외부 의존성 호출 없음
        verify(reviewRepo, times(1)).findRecentReviewBookIds(50);

        verifyNoInteractions(bookRepo, geminiClient, esUpdater);
        verifyNoMoreInteractions(reviewRepo);
    }

    @Test
    void runOnce_skips_whenGeminiReturnsBlank() {
        // given
        when(reviewRepo.findRecentReviewBookIds(50)).thenReturn(List.of(10L));
        when(reviewRepo.findRecentReviewContentsByBookId(10L, 30))
                .thenReturn(List.of("좋아요"));
        when(bookRepo.findIsbnByBookId(10L)).thenReturn(Optional.of("9780000000002"));
        when(geminiClient.generateText(anyString())).thenReturn("   "); // blank

        // when
        service.runOnce(50, 30);

        // then -> summary가 blank라 ES 업데이트는 호출되면 안 됨
        verify(esUpdater, never()).updateReviewSummaryByIsbn(anyString(), anyMap());

        // indexName을 얻기 위해 esProps도 호출되면 안 됨
        verifyNoInteractions(esProps);

        verify(reviewRepo, times(1)).findRecentReviewBookIds(50);
        verify(reviewRepo, times(1)).findRecentReviewContentsByBookId(10L, 30);
        verify(bookRepo, times(1)).findIsbnByBookId(10L);
        verify(geminiClient, times(1)).generateText(anyString());
    }
}
