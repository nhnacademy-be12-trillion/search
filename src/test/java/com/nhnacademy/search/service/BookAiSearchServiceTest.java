package com.nhnacademy.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.search.client.GeminiClient;
import com.nhnacademy.search.config.AiSearchProperties;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import com.nhnacademy.search.dto.BookSearchResult;
import com.nhnacademy.search.dto.BookSortOption;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class BookAiSearchServiceTest {

    @Mock private WebClient elasticsearchWebClient;

    // ✅ 체이닝 때문에 deep stubs 권장
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ElasticsearchProperties esProps;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AiSearchProperties aiProps;

    @Mock private WebClient ollamaWebClient;
    @Mock private WebClient rerankerWebClient;
    @Mock private GeminiClient geminiClient;
    @Mock private AiSearchCacheService aiCache;
    @Mock private BookSearchService legacySearchService;

    private BookAiSearchService svc;

    @BeforeEach
    void setUp() {
        // index
        when(esProps.getIndex().getBook()).thenReturn("trillion_books_dev_v2");

        // AI props (필수 사용 값들)
        when(aiProps.getOllama().getEmbedModel()).thenReturn("bge-m3");

        when(aiProps.getSearch().getMinCandidates()).thenReturn(50);
        when(aiProps.getSearch().getMaxCandidates()).thenReturn(200);
        when(aiProps.getSearch().getCandidateMultiplier()).thenReturn(5);
        when(aiProps.getSearch().getRerankTextMaxLen()).thenReturn(1200);
        when(aiProps.getSearch().getEmbeddingField()).thenReturn("embeddingVector");
        when(aiProps.getSearch().getScriptSource()).thenReturn("cosineSimilarity(params.qv, '{field}') + 1.0");

        svc = new BookAiSearchService(
                elasticsearchWebClient,
                esProps,
                aiProps,
                ollamaWebClient,
                rerankerWebClient,
                geminiClient,
                new ObjectMapper(),
                aiCache,
                legacySearchService
        );
    }

    // ---------------------------
    // helper: cache가 supplier 실행하게 만들어서 AI 파이프라인을 실제로 태움
    // ---------------------------
    @SuppressWarnings("unchecked")
    private void cacheRunsSupplier() {
        when(aiCache.getOrCompute(any(BookSearchRequest.class), any()))
                .thenAnswer(inv -> ((Supplier<BookSearchResponse>) inv.getArgument(1)).get());
    }

    // ---------------------------
    // helper: Ollama embedding stub
    // ---------------------------
    private void stubOllamaEmbedding(List<Float> vec) {
        WebClient.RequestBodyUriSpec post = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headers = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec resp = mock(WebClient.ResponseSpec.class);

        when(ollamaWebClient.post()).thenReturn(post);
        when(post.uri("/api/embeddings")).thenReturn(post);
        doReturn(headers).when(post).bodyValue(any());
        when(headers.retrieve()).thenReturn(resp);

        Map<String, Object> body = Map.of("embedding", vec);
        doReturn(Mono.just(body)).when(resp)
                .bodyToMono(any(ParameterizedTypeReference.class));
    }

    // ---------------------------
    // helper: ES search stub (+ query captor)
    // ---------------------------
    private ArgumentCaptor<Map<String, Object>> stubEsSearchReturn(Map<String, Object> esResponse) {
        WebClient.RequestBodyUriSpec post = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headers = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec resp = mock(WebClient.ResponseSpec.class);

        when(elasticsearchWebClient.post()).thenReturn(post);
        when(post.uri("/trillion_books_dev_v2/_search")).thenReturn(post);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass((Class) Map.class);
        doReturn(headers).when(post).bodyValue(captor.capture());

        when(headers.retrieve()).thenReturn(resp);
        doReturn(Mono.just(esResponse)).when(resp)
                .bodyToMono(any(ParameterizedTypeReference.class));

        return captor;
    }

    // ---------------------------
    // helper: reranker stub
    // ---------------------------
    private void stubRerankerReturn(List<Map<String, Object>> rerankResp) {
        WebClient.RequestBodyUriSpec post = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headers = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec resp = mock(WebClient.ResponseSpec.class);

        when(rerankerWebClient.post()).thenReturn(post);
        when(post.uri("/rerank")).thenReturn(post);
        doReturn(headers).when(post).bodyValue(any());
        when(headers.retrieve()).thenReturn(resp);

        doReturn(Mono.just(rerankResp)).when(resp)
                .bodyToMono(any(ParameterizedTypeReference.class));
    }

    // ---------------------------
    // ES hits 생성 helper
    // ---------------------------
    private static Map<String, Object> esHit(String id, float score, String isbn, String title, String editionDate, int price) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("isbn", isbn);
        metadata.put("title", title);
        metadata.put("subtitle", null);
        metadata.put("author", "author");
        metadata.put("publisher", "pub");
        metadata.put("price", price);
        metadata.put("salePrice", price - 1000);
        metadata.put("imageUrl", "img");
        metadata.put("editionPublishDate", editionDate);
        metadata.put("tags", List.of("t1"));
        metadata.put("reviewSummary", "rs");
        metadata.put("content", "ct");

        Map<String, Object> source = new HashMap<>();
        source.put("id", id);
        source.put("metadata", metadata);
        source.put("ratingAvg", 4.2f);
        source.put("reviewCount", 10);
        source.put("popularityScore", 5.0);

        Map<String, Object> hit = new HashMap<>();
        hit.put("_score", score);
        hit.put("_source", source);
        return hit;
    }

    // ============================================================
    // cache에 들어가는 request가 정규화
    // ============================================================
    @Test
    void search_passesNormalizedRequest_toCache() {
        BookSearchRequest req = new BookSearchRequest("q", BookSortOption.RELEVANCE, -5, 999);

        when(aiCache.getOrCompute(any(BookSearchRequest.class), any()))
                .thenAnswer(inv -> {
                    BookSearchRequest normalized = inv.getArgument(0);
                    assertEquals(0, normalized.page());
                    assertEquals(50, normalized.size()); // max=50
                    return new BookSearchResponse(List.of(), 0L, normalized.page(), normalized.size());
                });

        BookSearchResponse out = svc.search(req);
        assertEquals(0, out.page());
        assertEquals(50, out.size());
    }

    // ============================================================
    // ES hits 비면 결과 empty
    // ============================================================
    @Test
    void search_aiPipeline_returnsEmpty_whenEsHasNoHits() {
        cacheRunsSupplier();

        stubOllamaEmbedding(List.of(0.1f, 0.2f, 0.3f));

        Map<String, Object> esResp = Map.of("hits", Map.of("hits", List.of()));
        stubEsSearchReturn(esResp);

        // reranker 호출까지 안 가도 됨(후보 empty로 return)
        BookSearchResponse out = svc.search(new BookSearchRequest("abc", BookSortOption.RELEVANCE, 0, 10));

        assertEquals(0, out.total());
        assertTrue(out.results().isEmpty());
        verify(legacySearchService, never()).search(any());
    }

    // ============================================================
    // rerank가 상위 순서를 바꾼다 (RELEVANCE, gemini 생략하려면 query는 ""로)
    // ============================================================
    @Test
    void search_aiPipeline_appliesRerankOrdering_whenRelevanceSort() {
        cacheRunsSupplier();

        // query를 ""로 하면 gemini 검증 단계는 skip됨 (verifyAndEnrichWithGemini에서 blank면 return)
        String query = "";

        stubOllamaEmbedding(List.of(0.1f, 0.2f, 0.3f));

        List<Map<String, Object>> hits = List.of(
                esHit("1", 2.0f, "isbn1", "t1", "2024-01-01", 10000),
                esHit("2", 1.0f, "isbn2", "t2", "2024-01-02", 11000)
        );
        Map<String, Object> esResp = Map.of("hits", Map.of("hits", hits));
        stubEsSearchReturn(esResp);

        // rerank: index=1이 더 점수 높게 -> 2가 1보다 위로
        stubRerankerReturn(List.of(
                Map.of("index", 1, "score", 0.9),
                Map.of("index", 0, "score", 0.1)
        ));

        BookSearchResponse out = svc.search(new BookSearchRequest(query, BookSortOption.RELEVANCE, 0, 10));
        List<BookSearchResult> content = out.results();

        assertEquals(2, content.size());
        assertEquals("2", content.get(0).id());
        assertEquals("1", content.get(1).id());
    }

    // ============================================================
    //  NEW 정렬이면 editionPublishDate 최신이 먼저 온다
    // ============================================================
    @Test
    void search_aiPipeline_sortNew_overridesRelevanceOrder() {
        cacheRunsSupplier();

        stubOllamaEmbedding(List.of(0.1f, 0.2f, 0.3f));

        // 일부러 점수/순서와 날짜를 엇갈리게
        List<Map<String, Object>> hits = List.of(
                esHit("old", 100f, "isbnOld", "old", "2024-01-01", 10000),
                esHit("new",  1f, "isbnNew", "new", "2025-01-01", 11000)
        );
        Map<String, Object> esResp = Map.of("hits", Map.of("hits", hits));
        stubEsSearchReturn(esResp);

        // rerank가 뭘 하든, NEW 정렬이면 editionEpochMillis desc 우선
        stubRerankerReturn(List.of(
                Map.of("index", 0, "score", 0.99),
                Map.of("index", 1, "score", 0.01)
        ));

        BookSearchResponse out = svc.search(new BookSearchRequest("q", BookSortOption.NEW, 0, 10));
        assertEquals("new", out.results().get(0).id());
        assertEquals("old", out.results().get(1).id());
    }

    // ============================================================
    // RATING 정렬이면 ES query에 ratingCount gte 100 필터가 들어간다
    // ============================================================
    @Test
    void search_aiPipeline_esQuery_containsRatingCountFilter_whenSortRating() {
        cacheRunsSupplier();

        stubOllamaEmbedding(List.of(0.1f, 0.2f, 0.3f));

        // hits empty로 만들어도 query는 만들어져서 bodyValue 캡처 가능
        Map<String, Object> esResp = Map.of("hits", Map.of("hits", List.of()));
        ArgumentCaptor<Map<String, Object>> cap = stubEsSearchReturn(esResp);

        svc.search(new BookSearchRequest("q", BookSortOption.RATING, 0, 10));

        Map<String, Object> esQuery = cap.getValue();
        assertNotNull(esQuery);

        // query.script_score.query.bool.filter 안에
        // - exists embeddingVector
        // - range ratingCount gte 100
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) esQuery.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> scriptScore = (Map<String, Object>) query.get("script_score");
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) scriptScore.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> bool = (Map<String, Object>) inner.get("bool");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filters = (List<Map<String, Object>>) bool.get("filter");

        boolean hasExists = filters.stream().anyMatch(f -> f.containsKey("exists"));
        boolean hasRatingRange = filters.stream().anyMatch(f -> {
            Object r = f.get("range");
            if (!(r instanceof Map<?,?> rm)) return false;
            Object rc = rm.get("ratingCount");
            if (!(rc instanceof Map<?,?> rcm)) return false;
            return Objects.equals(rcm.get("gte"), 100);
        });

        assertTrue(hasExists);
        assertTrue(hasRatingRange);
    }

    // ============================================================
    // 유틸 / 파서 private 테스트들도 그대로
    // ============================================================
    @Test
    public void private_parseDateToEpochMillis_supportsMultipleFormats() throws Exception {
        Method m = BookAiSearchService.class.getDeclaredMethod("parseDateToEpochMillis", String.class);
        m.setAccessible(true);

        Long a = (Long) m.invoke(svc, "2025-12-30");
        assertNotNull(a);

        String odt = "2025-12-30T12:34:56+09:00";
        Long b = (Long) m.invoke(svc, odt);
        assertEquals(OffsetDateTime.parse(odt).toInstant().toEpochMilli(), b);

        Long c = (Long) m.invoke(svc, "2025-12-30T03:34:56Z");
        assertNotNull(c);

        Long d = (Long) m.invoke(svc, "not-a-date");
        assertNull(d);

        Long e = (Long) m.invoke(svc, (String) null);
        assertNull(e);
    }
}
