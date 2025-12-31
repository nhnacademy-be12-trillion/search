package com.nhnacademy.search.service;

import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.repository.BookAuthorReadRepository;
import com.nhnacademy.search.repository.BookIndexingReadRepository;
import com.nhnacademy.search.repository.BookStatsReadRepository;
import com.nhnacademy.search.repository.BookTagReadRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.time.LocalDate;


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class BookSingleIndexServiceTest {

    @Mock private BookIndexingReadRepository bookIndexingReadRepository;
    @Mock private BookAuthorReadRepository bookAuthorReadRepository;
    @Mock private BookTagReadRepository bookTagReadRepository;
    @Mock private BookStatsReadRepository bookStatsReadRepository;

    // chained call 때문에 deep stubs 사용
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ElasticsearchProperties elasticsearchProperties;

    @Mock private WebClient esWebClient;

    // WebClient chain mocks
    private WebClient.RequestBodyUriSpec putUriSpec;
    private WebClient.RequestBodySpec putBodySpec;
    private WebClient.RequestHeadersSpec<?> putHeadersSpec;
    private WebClient.ResponseSpec putRespSpec;

    private WebClient.RequestHeadersUriSpec<?> deleteUriSpec;
    private WebClient.RequestHeadersSpec<?> deleteHeadersSpec;
    private WebClient.ResponseSpec deleteRespSpec;

    private BookSingleIndexService service;

    private final String indexName = "trillion_books_dev_v2";

    @BeforeEach
    void setUp() {
        when(elasticsearchProperties.getIndex().getBook()).thenReturn(indexName);

        // PUT chain
        putUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        putBodySpec = mock(WebClient.RequestBodySpec.class);

        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec putHeadersSpecRaw = mock(WebClient.RequestHeadersSpec.class);
        @SuppressWarnings("unchecked")
        WebClient.RequestHeadersSpec<?> putHeadersSpec = (WebClient.RequestHeadersSpec<?>) putHeadersSpecRaw;

        putRespSpec = mock(WebClient.ResponseSpec.class);

        when(esWebClient.put()).thenReturn(putUriSpec);

        doReturn(putBodySpec).when(putUriSpec)
                .uri(eq("/{index}/_doc/{id}"), any(), any());

        when(putBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(putBodySpec);

        doReturn(putHeadersSpec).when(putBodySpec).bodyValue(any());

        when(putHeadersSpec.retrieve()).thenReturn(putRespSpec);

        when(putRespSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just("OK"));

        // DELETE chain
        deleteUriSpec = mock(WebClient.RequestHeadersUriSpec.class);

        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec deleteHeadersSpecRaw = mock(WebClient.RequestHeadersSpec.class);
        @SuppressWarnings("unchecked")
        WebClient.RequestHeadersSpec<?> deleteHeadersSpec = (WebClient.RequestHeadersSpec<?>) deleteHeadersSpecRaw;

        deleteRespSpec = mock(WebClient.ResponseSpec.class);

        doReturn(deleteUriSpec).when(esWebClient).delete();

        doReturn(deleteHeadersSpec).when(deleteUriSpec)
                .uri(eq("/{index}/_doc/{id}"), any(), any());

        when(deleteHeadersSpec.retrieve()).thenReturn(deleteRespSpec);
        when(deleteRespSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just("DELETED"));

        service = new BookSingleIndexService(
                bookIndexingReadRepository,
                bookAuthorReadRepository,
                bookTagReadRepository,
                bookStatsReadRepository,
                elasticsearchProperties,
                esWebClient
        );
    }


    @Test
    void upsertByBookId_should_putDoc_toES_and_build_expected_doc() {
        // given
        long bookId = 1L;

        BookIndexingReadRepository.BookIndexingRow base = mock(BookIndexingReadRepository.BookIndexingRow.class);
        when(base.bookId()).thenReturn(bookId);
        when(base.isbn()).thenReturn("9788986604009");
        when(base.bookName()).thenReturn("자바 맛보기");
        when(base.publisherName()).thenReturn("씨에이");
        when(base.bookRegularPrice()).thenReturn(12000);
        when(base.bookSalePrice()).thenReturn(10800);
        when(base.imageUrl()).thenReturn("http://img/x.png");
        when(base.bookPublicationDate()).thenReturn(LocalDate.parse("1995-12-01"));
        when(base.bookDescription()).thenReturn("설명...");
        when(base.bookReviewSummary()).thenReturn("요약...");

        when(bookIndexingReadRepository.findByBookId(bookId)).thenReturn(Optional.of(base));

        BookAuthorReadRepository.BookAuthorsRow authorsRow = mock(BookAuthorReadRepository.BookAuthorsRow.class);
        when(authorsRow.authorsCsv()).thenReturn("John December, 홍지택");
        when(bookAuthorReadRepository.findByBookIds(List.of(bookId))).thenReturn(Map.of(bookId, authorsRow));

        BookTagReadRepository.BookTagsRow tagsRow = mock(BookTagReadRepository.BookTagsRow.class);
        when(tagsRow.tagsCsv()).thenReturn("기록관리,  기록관리, 자바  ,");
        when(bookTagReadRepository.findByBookIds(List.of(bookId))).thenReturn(Map.of(bookId, tagsRow));

        BookStatsReadRepository.ReviewAgg agg = mock(BookStatsReadRepository.ReviewAgg.class);
        when(agg.reviewCount()).thenReturn(3);
        when(agg.ratingCount()).thenReturn(2);
        when(agg.ratingAvg()).thenReturn(4.5);
        when(bookStatsReadRepository.findReviewAggByBookIds(List.of(bookId))).thenReturn(Map.of(bookId, agg));
        when(bookStatsReadRepository.findViewCountByBookId(bookId)).thenReturn(10L);

        // when
        service.upsertByBookId(bookId);

        // then: bodyValue로 들어간 doc 검증
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> docCaptor = ArgumentCaptor.forClass(Map.class);
        verify(putBodySpec, times(1)).bodyValue(docCaptor.capture());

        Map<String, Object> doc = docCaptor.getValue();
        assertEquals("1", doc.get("id"));
        assertEquals("9788986604009", doc.get("isbn"));
        assertEquals("자바 맛보기", doc.get("title"));
        assertEquals("씨에이", doc.get("publisherName"));
        assertEquals("설명...", doc.get("bookContent"));
        assertEquals("John December, 홍지택", doc.get("authorName"));
        assertEquals(3, doc.get("reviewCount"));
        assertEquals(2, doc.get("ratingCount"));
        assertEquals(4.5f, (Float) doc.get("ratingAvg"));
        assertEquals(10.0f, (Float) doc.get("popularityScore"));

        // embeddingVector는 일부러 안 넣는다고 했으니 없어야 함
        assertFalse(doc.containsKey("embeddingVector"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
        assertNotNull(metadata);
        assertEquals("9788986604009", metadata.get("isbn"));
        assertEquals("자바 맛보기", metadata.get("title"));
        assertEquals("씨에이", metadata.get("publisher"));
        assertEquals(12000, metadata.get("price"));
        assertEquals(10800, metadata.get("salePrice"));
        assertEquals("http://img/x.png", metadata.get("imageUrl"));
        assertEquals(LocalDate.parse("1995-12-01"), metadata.get("editionPublishDate"));
        assertEquals("요약...", metadata.get("reviewSummary"));

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) metadata.get("tags");
        assertEquals(List.of("기록관리", "자바"), tags); // trim + distinct 확인
    }

    @Test
    void upsertByIsbnWithRetry_should_retry_and_eventually_upsert() {
        // given
        long bookId = 7L;

        BookIndexingReadRepository.BookIndexingRow base = mock(BookIndexingReadRepository.BookIndexingRow.class);
        when(base.bookId()).thenReturn(bookId);
        when(base.isbn()).thenReturn("978123");
        when(base.bookName()).thenReturn("테스트");
        when(base.publisherName()).thenReturn("출판사");
        when(base.bookRegularPrice()).thenReturn(10000);
        when(base.bookSalePrice()).thenReturn(9000);
        when(base.imageUrl()).thenReturn(null);
        when(base.bookPublicationDate()).thenReturn(LocalDate.parse("2025-01-01"));
        when(base.bookDescription()).thenReturn("desc");
        when(base.bookReviewSummary()).thenReturn(null);

        // 1~2번째는 empty, 3번째에 조회됨 -> sleep이 약간(수백 ms) 들어감
        when(bookIndexingReadRepository.findByIsbn("978123"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(base));

        when(bookAuthorReadRepository.findByBookIds(List.of(bookId))).thenReturn(Map.of());
        when(bookTagReadRepository.findByBookIds(List.of(bookId))).thenReturn(Map.of());
        when(bookStatsReadRepository.findReviewAggByBookIds(List.of(bookId))).thenReturn(Map.of());
        when(bookStatsReadRepository.findViewCountByBookId(bookId)).thenReturn(0L);

        // when
        service.upsertByIsbnWithRetry("978-123");

        // then
        verify(bookIndexingReadRepository, times(3)).findByIsbn("978123");
        verify(putBodySpec, times(1)).bodyValue(any());
    }

    @Test
    void deleteByBookId_should_ignore_404() {
        // given: delete 요청이 404를 던지도록
        WebClientResponseException notFound = WebClientResponseException.create(
                404, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8
        );
        when(deleteRespSpec.bodyToMono(String.class)).thenReturn(Mono.error(notFound));

        // when / then (예외 없어야 함)
        assertDoesNotThrow(() -> service.deleteByBookId(999L));
    }

    @Test
    void deleteByBookId_should_rethrow_non404() {
        // given: 500
        WebClientResponseException serverError = WebClientResponseException.create(
                500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8
        );
        when(deleteRespSpec.bodyToMono(String.class)).thenReturn(Mono.error(serverError));

        // when / then
        assertThrows(WebClientResponseException.class, () -> service.deleteByBookId(1L));
    }

    @Test
    void upsertByIsbnWithRetry_should_throw_NotFoundException_when_never_found() {
        // given: 계속 empty (최대 8회 시도 + backoff sleep으로 약 5초 걸릴 수 있음)
        when(bookIndexingReadRepository.findByIsbn("978000")).thenReturn(Optional.empty());

        // when / then: 테스트가 너무 오래 걸리면 안 되니 timeout으로 감싸기
        assertTimeoutPreemptively(Duration.ofSeconds(7), () -> {
            assertThrows(BookSingleIndexService.NotFoundException.class,
                    () -> service.upsertByIsbnWithRetry("978-000"));
        });
    }
}
