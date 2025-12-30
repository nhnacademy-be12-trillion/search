package com.nhnacademy.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.search.config.AiSearchProperties;
import com.nhnacademy.search.config.ElasticsearchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookEmbeddingBackfillServiceTest {

    @Mock private WebClient esWebClient;
    @Mock private WebClient ollamaWebClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ElasticsearchProperties esProps;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AiSearchProperties aiProps;

    private BookEmbeddingBackfillService newService() {
        return new BookEmbeddingBackfillService(
                esWebClient,
                ollamaWebClient,
                esProps,
                aiProps,
                new ObjectMapper()
        );
    }

    @Test
    public void fillMissingEmbeddings_throws_whenIndexBlank() {
        when(esProps.getIndex().getBook()).thenReturn("  ");

        BookEmbeddingBackfillService svc = newService();

        assertThrows(IllegalStateException.class, () -> svc.fillMissingEmbeddings(10, 0));
        verifyNoInteractions(esWebClient, ollamaWebClient);
    }

    @Test
    public void fillMissingEmbeddings_returns0_whenNoHits() {
        when(esProps.getIndex().getBook()).thenReturn("books");
        when(aiProps.getSearch().getEmbeddingField()).thenReturn("embeddingVector");

        WebClient.RequestBodyUriSpec post = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec searchSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec searchHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec searchResp = mock(WebClient.ResponseSpec.class);

        when(esWebClient.post()).thenReturn(post);
        when(post.uri(contains("/books/_search"))).thenReturn(searchSpec);
        when(searchSpec.bodyValue(any())).thenReturn(searchHeaders);
        when(searchHeaders.retrieve()).thenReturn(searchResp);
        when(searchResp.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(esResp(List.of())));

        BookEmbeddingBackfillService svc = newService();

        int updated = svc.fillMissingEmbeddings(10, 0);

        assertEquals(0, updated);
        verify(post, never()).uri(eq("/_bulk"));
        verifyNoInteractions(ollamaWebClient);
    }

    @Test
    public void fillMissingEmbeddings_skipsBlankText_andMovesSearchAfter() {
        when(esProps.getIndex().getBook()).thenReturn("books");
        when(aiProps.getSearch().getEmbeddingField()).thenReturn("embeddingVector");

        Map<String, Object> hitBlank = hit("1", 10, Map.of("metadata", Map.of(
                "title", " ",
                "author", "",
                "publisher", "  ",
                "content", ""
        )));

        WebClient.RequestBodyUriSpec post = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec searchSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec searchHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec searchResp = mock(WebClient.ResponseSpec.class);

        when(esWebClient.post()).thenReturn(post);
        when(post.uri(contains("/books/_search"))).thenReturn(searchSpec);
        when(searchSpec.bodyValue(any())).thenReturn(searchHeaders);
        when(searchHeaders.retrieve()).thenReturn(searchResp);
        when(searchResp.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(esResp(List.of(hitBlank))),
                        Mono.just(esResp(List.of())));

        BookEmbeddingBackfillService svc = newService();

        int updated = svc.fillMissingEmbeddings(10, 0);

        assertEquals(0, updated);
        verify(post, times(2)).uri(contains("/books/_search"));
        verify(post, never()).uri(eq("/_bulk"));
        verifyNoInteractions(ollamaWebClient);
    }

    @Test
    public void fillMissingEmbeddings_updatesOneDoc_callsBulk_andStops() {
        when(esProps.getIndex().getBook()).thenReturn("books");
        when(aiProps.getSearch().getEmbeddingField()).thenReturn("embeddingVector");
        when(aiProps.getOllama().getEmbedModel()).thenReturn("nomic-embed-text");

        Map<String, Object> hit1 = hit("1", 10, Map.of("metadata", Map.of(
                "title", "t",
                "author", "a",
                "publisher", "p",
                "content", "c"
        )));

        WebClient.RequestBodyUriSpec esPost = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec esSearchSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec esSearchHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec esSearchResp = mock(WebClient.ResponseSpec.class);

        WebClient.RequestBodySpec esBulkSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec esBulkHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec esBulkResp = mock(WebClient.ResponseSpec.class);

        when(esWebClient.post()).thenReturn(esPost);

        when(esPost.uri(contains("/books/_search"))).thenReturn(esSearchSpec);
        when(esSearchSpec.bodyValue(any())).thenReturn(esSearchHeaders);
        when(esSearchHeaders.retrieve()).thenReturn(esSearchResp);
        when(esSearchResp.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(esResp(List.of(hit1))),
                        Mono.just(esResp(List.of())));

        when(esPost.uri(eq("/_bulk"))).thenReturn(esBulkSpec);
        when(esBulkSpec.contentType(eq(MediaType.APPLICATION_NDJSON))).thenReturn(esBulkSpec);
        when(esBulkSpec.bodyValue(anyString())).thenReturn(esBulkHeaders);
        when(esBulkHeaders.retrieve()).thenReturn(esBulkResp);
        when(esBulkResp.bodyToMono(eq(String.class))).thenReturn(Mono.just("{\"errors\":false}"));

        WebClient.RequestBodyUriSpec olPost = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec olSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec olHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec olResp = mock(WebClient.ResponseSpec.class);

        when(ollamaWebClient.post()).thenReturn(olPost);
        when(olPost.uri(eq("/api/embeddings"))).thenReturn(olSpec);
        when(olSpec.bodyValue(any())).thenReturn(olHeaders);
        when(olHeaders.retrieve()).thenReturn(olResp);
        when(olResp.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(Map.of("embedding", List.of(0.1, "0.2"))));

        BookEmbeddingBackfillService svc = newService();

        int updated = svc.fillMissingEmbeddings(10, 0);

        assertEquals(1, updated);

        verify(esPost, times(2)).uri(contains("/books/_search"));
        verify(esPost, times(1)).uri(eq("/_bulk"));
        verify(esBulkSpec).bodyValue(argThat((String s) ->
                s.contains("\"_id\":\"1\"") && s.contains("\"embeddingVector\"")
        ));
        verify(olPost, times(1)).uri(eq("/api/embeddings"));
    }

    @Test
    public void fillMissingEmbeddings_respectsMaxDocs() {
        when(esProps.getIndex().getBook()).thenReturn("books");
        when(aiProps.getSearch().getEmbeddingField()).thenReturn("embeddingVector");
        when(aiProps.getOllama().getEmbedModel()).thenReturn("nomic-embed-text");

        Map<String, Object> hit1 = hit("1", 10, Map.of("metadata", Map.of("title", "t1")));
        Map<String, Object> hit2 = hit("2", 11, Map.of("metadata", Map.of("title", "t2")));

        WebClient.RequestBodyUriSpec esPost = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec esSearchSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec esSearchHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec esSearchResp = mock(WebClient.ResponseSpec.class);

        WebClient.RequestBodySpec esBulkSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec esBulkHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec esBulkResp = mock(WebClient.ResponseSpec.class);

        when(esWebClient.post()).thenReturn(esPost);

        when(esPost.uri(contains("/books/_search"))).thenReturn(esSearchSpec);
        when(esSearchSpec.bodyValue(any())).thenReturn(esSearchHeaders);
        when(esSearchHeaders.retrieve()).thenReturn(esSearchResp);
        when(esSearchResp.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(esResp(List.of(hit1, hit2))));

        when(esPost.uri(eq("/_bulk"))).thenReturn(esBulkSpec);
        when(esBulkSpec.contentType(eq(MediaType.APPLICATION_NDJSON))).thenReturn(esBulkSpec);
        when(esBulkSpec.bodyValue(anyString())).thenReturn(esBulkHeaders);
        when(esBulkHeaders.retrieve()).thenReturn(esBulkResp);
        when(esBulkResp.bodyToMono(eq(String.class))).thenReturn(Mono.just("{\"errors\":false}"));

        WebClient.RequestBodyUriSpec olPost = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec olSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec olHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec olResp = mock(WebClient.ResponseSpec.class);

        when(ollamaWebClient.post()).thenReturn(olPost);
        when(olPost.uri(eq("/api/embeddings"))).thenReturn(olSpec);
        when(olSpec.bodyValue(any())).thenReturn(olHeaders);
        when(olHeaders.retrieve()).thenReturn(olResp);
        when(olResp.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(Map.of("embedding", List.of(0.1))));

        BookEmbeddingBackfillService svc = newService();

        int updated = svc.fillMissingEmbeddings(10, 1);

        assertEquals(1, updated);

        verify(esPost, times(1)).uri(contains("/books/_search"));
        verify(esPost, times(1)).uri(eq("/_bulk"));

        verify(esBulkSpec).bodyValue(argThat((String s) ->
                s.contains("\"_id\":\"1\"") && s.contains("\"embeddingVector\"")
        ));
        verify(olPost, times(1)).uri(eq("/api/embeddings"));
    }

    @Test
    public void private_embed_throws_whenRespNullOrMissingEmbedding() throws Exception {
        when(aiProps.getOllama().getEmbedModel()).thenReturn("m");

        WebClient.RequestBodyUriSpec olPost = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec olSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec olHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec olResp = mock(WebClient.ResponseSpec.class);

        when(ollamaWebClient.post()).thenReturn(olPost);
        when(olPost.uri(eq("/api/embeddings"))).thenReturn(olSpec);
        when(olSpec.bodyValue(any())).thenReturn(olHeaders);
        when(olHeaders.retrieve()).thenReturn(olResp);

        BookEmbeddingBackfillService svc = newService();
        Method m = BookEmbeddingBackfillService.class.getDeclaredMethod("embed", String.class);
        m.setAccessible(true);

        when(olResp.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.justOrEmpty(null));

        InvocationTargetException e1 = assertThrows(InvocationTargetException.class, () -> m.invoke(svc, "hi"));
        assertTrue(e1.getCause() instanceof IllegalStateException);

        when(olResp.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(Map.of()));

        InvocationTargetException e2 = assertThrows(InvocationTargetException.class, () -> m.invoke(svc, "hi"));
        assertTrue(e2.getCause() instanceof IllegalStateException);
    }

    private Map<String, Object> esResp(List<Map<String, Object>> hits) {
        return Map.of("hits", Map.of("hits", hits));
    }

    private Map<String, Object> hit(String id, Object sortVal, Map<String, Object> source) {
        return Map.of(
                "_id", id,
                "_source", source,
                "sort", List.of(sortVal)
        );
    }
}
