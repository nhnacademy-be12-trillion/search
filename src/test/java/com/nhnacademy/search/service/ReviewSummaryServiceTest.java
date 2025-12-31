package com.nhnacademy.search.service;

import com.nhnacademy.search.config.ElasticsearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewSummaryServiceTest {

    @Mock private WebClient elasticsearchWebClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ElasticsearchProperties properties;

    private ReviewSummaryService service;

    @BeforeEach
    void setUp() {
        when(properties.getIndex().getBook()).thenReturn("trillion_books_dev_v2");
        service = new ReviewSummaryService(elasticsearchWebClient, properties);
    }

    @Test
    void findReviewSummaryByIsbn_should_return_summary_when_hit_exists() {
        // given
        String isbn = "9788986604009";

        // ES 응답 형태: hits.hits[0]._source.metadata.reviewSummary
        Map<String, Object> esResponse = Map.of(
                "hits", Map.of(
                        "hits", List.of(
                                Map.of(
                                        "_source", Map.of(
                                                "metadata", Map.of(
                                                        "reviewSummary", "  요약입니다.  "
                                                )
                                        )
                                )
                        )
                )
        );

        // WebClient chain mocks
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);

        when(elasticsearchWebClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/trillion_books_dev_v2/_search")).thenReturn(postSpec);

        doReturn(headersSpec).when(postSpec).bodyValue(any());

        when(headersSpec.retrieve()).thenReturn(respSpec);

        doReturn(Mono.just(esResponse)).when(respSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        // when
        String out = service.findReviewSummaryByIsbn(isbn);

        // then
        assertEquals("요약입니다.", out);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> qCaptor = ArgumentCaptor.forClass(Map.class);
        verify(postSpec).bodyValue(qCaptor.capture());
        Map<String, Object> q = qCaptor.getValue();

        assertEquals(1, q.get("size"));
        assertEquals(List.of("metadata.reviewSummary"), q.get("_source"));

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) q.get("query");
        assertNotNull(query);
    }

    @Test
    void findReviewSummaryByIsbn_should_return_null_when_no_hits() {
        // given
        Map<String, Object> esResponse = Map.of(
                "hits", Map.of("hits", List.of())
        );

        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);

        when(elasticsearchWebClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/trillion_books_dev_v2/_search")).thenReturn(postSpec);
        doReturn(headersSpec).when(postSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(respSpec);
        doReturn(Mono.just(esResponse)).when(respSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        // when
        String out = service.findReviewSummaryByIsbn("978000");

        // then
        assertNull(out);
    }

    @Test
    void findReviewSummaryByIsbn_should_return_null_when_summary_blank() {
        // given
        Map<String, Object> esResponse = Map.of(
                "hits", Map.of(
                        "hits", List.of(
                                Map.of("_source", Map.of("metadata", Map.of("reviewSummary", "   ")))
                        )
                )
        );

        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);

        when(elasticsearchWebClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/trillion_books_dev_v2/_search")).thenReturn(postSpec);
        doReturn(headersSpec).when(postSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(respSpec);
        doReturn(Mono.just(esResponse)).when(respSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        // when
        String out = service.findReviewSummaryByIsbn("978111");

        // then
        assertNull(out);
    }

    @Test
    void findReviewSummaryByIsbn_should_return_null_when_esResponse_null() {
        // given
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);

        when(elasticsearchWebClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/trillion_books_dev_v2/_search")).thenReturn(postSpec);
        doReturn(headersSpec).when(postSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(respSpec);

        // ES 응답이 null로 block
        doReturn(Mono.<Map<String, Object>>justOrEmpty(null)).when(respSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        // when
        String out = service.findReviewSummaryByIsbn("978222");

        // then
        assertNull(out);
    }
}
