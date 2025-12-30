package com.nhnacademy.search.service;

import com.nhnacademy.search.client.GeminiClient;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.es.EsReviewSummaryUpdater;
import com.nhnacademy.search.repository.BookIsbnReadRepository;
import com.nhnacademy.search.repository.BookWriteRepository;
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
    @Mock BookWriteRepository bookWriteRepo;

    ReviewSummaryPipelineService service;

    @BeforeEach
    void setUp() {
        service = new ReviewSummaryPipelineService(reviewRepo, bookRepo, bookWriteRepo, geminiClient, esUpdater, esProps);
    }

    @Test
    void runOnce_updatesEs_whenReviewsExist() {
        when(reviewRepo.findRecentReviewBookIds(50)).thenReturn(List.of(10L, 20L));

        when(reviewRepo.countAllReviewContentsByBookId(10L)).thenReturn(30);
        when(reviewRepo.countAllReviewContentsByBookId(20L)).thenReturn(0);

        when(reviewRepo.findRecentReviewContentsByBookId(10L, 30))
                .thenReturn(List.of("재밌어요", "구성이 좋아요"));

        when(bookRepo.findIsbnByBookId(10L)).thenReturn(Optional.of("9780000000001"));
        when(geminiClient.generateText(anyString()))
                .thenReturn("장점과 단점을 모두 포함한 3~5문장 요약");

        when(esProps.getIndex()).thenReturn(indexProps);
        when(indexProps.getBook()).thenReturn("trillion_books");

        service.runOnce(50, 30);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass((Class) Map.class);

        verify(esUpdater, times(1))
                .updateReviewSummaryByIsbn(eq("trillion_books"), captor.capture());

        Map<String, String> isbnToSummary = captor.getValue();
        assertEquals(1, isbnToSummary.size());
        assertEquals("장점과 단점을 모두 포함한 3~5문장 요약", isbnToSummary.get("9780000000001"));

        verify(reviewRepo, never()).findRecentReviewContentsByBookId(20L, 30);
        verify(bookRepo, never()).findIsbnByBookId(20L);

        verify(geminiClient, times(1)).generateText(anyString());
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
        when(reviewRepo.findRecentReviewBookIds(50)).thenReturn(List.of(10L));

        when(reviewRepo.countAllReviewContentsByBookId(10L)).thenReturn(30);

        when(reviewRepo.findRecentReviewContentsByBookId(10L, 30))
                .thenReturn(List.of("좋아요"));
        when(bookRepo.findIsbnByBookId(10L)).thenReturn(Optional.of("9780000000002"));
        when(geminiClient.generateText(anyString())).thenReturn("   ");

        service.runOnce(50, 30);

        verify(esUpdater, never()).updateReviewSummaryByIsbn(anyString(), anyMap());
        verify(esProps, never()).getIndex();

        verify(reviewRepo).findRecentReviewBookIds(50);
        verify(reviewRepo).countAllReviewContentsByBookId(10L);
        verify(reviewRepo).findRecentReviewContentsByBookId(10L, 30);
        verify(bookRepo).findIsbnByBookId(10L);
        verify(geminiClient).generateText(anyString());
    }

}
