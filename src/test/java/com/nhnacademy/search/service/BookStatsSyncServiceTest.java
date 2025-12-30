package com.nhnacademy.search.service;

import com.nhnacademy.search.config.BookStatsSyncProperties;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.es.EsBookStatsUpdater;
import com.nhnacademy.search.repository.BookStatsReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookStatsSyncServiceTest {

    @Mock private BookStatsReadRepository repo;
    @Mock private EsBookStatsUpdater esUpdater;
    @Mock private ElasticsearchProperties esProps;
    @Mock private ElasticsearchProperties.Index index;
    @Mock private BookStatsSyncProperties props;

    private BookStatsSyncService service;

    @BeforeEach
    void setUp() {
        when(esProps.getIndex()).thenReturn(index);
        when(index.getBook()).thenReturn("books");
        service = new BookStatsSyncService(repo, esUpdater, esProps, props);
    }

    @Test
    void runFullSync_updatesStats_byIsbn_and_skipsBlankIsbn() {
        when(props.getPageSize()).thenReturn(2);
        when(props.getUbqChunkSize()).thenReturn(100);

        BookStatsReadRepository.BookRow b1 = mock(BookStatsReadRepository.BookRow.class);
        when(b1.bookId()).thenReturn(1L);
        when(b1.isbn()).thenReturn("isbn-1");
        when(b1.viewCount()).thenReturn(7L);

        BookStatsReadRepository.BookRow b2 = mock(BookStatsReadRepository.BookRow.class);
        when(b2.bookId()).thenReturn(2L);
        when(b2.isbn()).thenReturn("   ");

        when(repo.findBookPage(0L, 2)).thenReturn(List.of(b1, b2));
        when(repo.findBookPage(2L, 2)).thenReturn(List.of());

        BookStatsReadRepository.ReviewAgg agg1 = mock(BookStatsReadRepository.ReviewAgg.class);
        when(agg1.reviewCount()).thenReturn(3);
        when(agg1.ratingCount()).thenReturn(2);
        when(agg1.ratingAvg()).thenReturn(4.5);

        when(repo.findReviewAggByBookIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, agg1));

        ArgumentCaptor<Map<String, EsBookStatsUpdater.BookStats>> captor = ArgumentCaptor.forClass(Map.class);

        service.runFullSync();

        verify(esUpdater).updateBookStatsByIsbn(eq("books"), captor.capture(), eq(100));

        Map<String, EsBookStatsUpdater.BookStats> sent = captor.getValue();
        assertEquals(1, sent.size());
        assertTrue(sent.containsKey("isbn-1"));

        EsBookStatsUpdater.BookStats stats = sent.get("isbn-1");
        assertEquals(3, stats.reviewCount());
        assertEquals(2, stats.ratingCount());
        assertEquals(4.5, stats.ratingAvg());
        assertEquals(7.0, stats.popularityScore());
    }

    @Test
    void runFullSync_doesNotCallUpdater_whenNoValidIsbn() {
        when(props.getPageSize()).thenReturn(2);

        BookStatsReadRepository.BookRow b1 = mock(BookStatsReadRepository.BookRow.class);
        when(b1.bookId()).thenReturn(1L);
        when(b1.isbn()).thenReturn(null);

        BookStatsReadRepository.BookRow b2 = mock(BookStatsReadRepository.BookRow.class);
        when(b2.bookId()).thenReturn(2L);
        when(b2.isbn()).thenReturn("   ");

        when(repo.findBookPage(0L, 2)).thenReturn(List.of(b1, b2));
        when(repo.findBookPage(2L, 2)).thenReturn(List.of());

        when(repo.findReviewAggByBookIds(List.of(1L, 2L)))
                .thenReturn(new LinkedHashMap<>());

        service.runFullSync();

        verify(esUpdater, never()).updateBookStatsByIsbn(anyString(), anyMap(), anyInt());
    }
}
