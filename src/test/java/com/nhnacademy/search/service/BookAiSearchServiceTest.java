package com.nhnacademy.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.search.client.GeminiClient;
import com.nhnacademy.search.config.AiSearchProperties;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import com.nhnacademy.search.dto.BookSortOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookAiSearchServiceTest {

    @Mock private WebClient elasticsearchWebClient;
    @Mock private ElasticsearchProperties esProps;
    @Mock private AiSearchProperties aiProps;
    @Mock private WebClient ollamaWebClient;
    @Mock private WebClient rerankerWebClient;
    @Mock private GeminiClient geminiClient;
    @Mock private AiSearchCacheService aiCache;
    @Mock private BookSearchService legacySearchService;

    private BookAiSearchService newService() {
        return new BookAiSearchService(
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

    @Test
    public void search_usesCacheAndReturns_whenCacheWorks() {
        BookAiSearchService svc = newService();

        BookSearchRequest req = new BookSearchRequest("spring", BookSortOption.RELEVANCE, 0, 10);
        BookSearchResponse cached = new BookSearchResponse(List.of(), 10L, 0, 10);

        when(aiCache.getOrCompute(any(BookSearchRequest.class), any())).thenReturn(cached);

        BookSearchResponse out = svc.search(req);

        assertSame(cached, out);
        verify(legacySearchService, never()).search(any());
    }

    @Test
    public void search_fallbacksToLegacy_whenCacheOrAiThrows_andPassesNormalizedRequest() {
        BookAiSearchService svc = newService();

        when(aiCache.getOrCompute(any(BookSearchRequest.class), any()))
                .thenThrow(new RuntimeException("ai broken"));

        BookSearchResponse fallback = new BookSearchResponse(List.of(), 0L, 0, 10);
        when(legacySearchService.search(any(BookSearchRequest.class))).thenReturn(fallback);

        BookSearchRequest req = new BookSearchRequest("  Query  ", BookSortOption.NEW, -3, 0);

        BookSearchResponse out = svc.search(req);

        assertSame(fallback, out);

        ArgumentCaptor<BookSearchRequest> cap = ArgumentCaptor.forClass(BookSearchRequest.class);
        verify(legacySearchService).search(cap.capture());

        BookSearchRequest normalized = cap.getValue();
        assertEquals(0, normalized.page());
        assertEquals(10, normalized.size());
        assertEquals("  Query  ", normalized.query());
        assertEquals(BookSortOption.NEW, normalized.sort());
    }

    @Test
    public void private_parseDateToEpochMillis_supportsMultipleFormats() throws Exception {
        BookAiSearchService svc = newService();

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

    @Test
    public void private_extractJsonArray_stripsCodeFence() throws Exception {
        BookAiSearchService svc = newService();

        Method m = BookAiSearchService.class.getDeclaredMethod("extractJsonArray", String.class);
        m.setAccessible(true);

        String raw = "```json\n[{\"id\":\"1\"}]\n```";
        String out = (String) m.invoke(svc, raw);
        assertEquals("[{\"id\":\"1\"}]", out);

        assertEquals("[]", (String) m.invoke(svc, "hello"));
        assertEquals("[]", (String) m.invoke(svc, (String) null));
    }

    @Test
    public void private_parseGeminiEvals_parsesJsonArray_andIgnoresMissingId() throws Exception {
        BookAiSearchService svc = newService();

        Method m = BookAiSearchService.class.getDeclaredMethod("parseGeminiEvals", String.class);
        m.setAccessible(true);

        String raw = """
                ```json
                [
                  {"id":"a","relevance":65,"reason":"ok"},
                  {"relevance":10,"reason":"no id"}
                ]
                ```
                """;

        @SuppressWarnings("unchecked")
        List<Object> evals = (List<Object>) m.invoke(svc, raw);

        assertEquals(1, evals.size());

        Object first = evals.get(0);
        Method id = first.getClass().getDeclaredMethod("id");
        Method relevance = first.getClass().getDeclaredMethod("relevance");
        Method reason = first.getClass().getDeclaredMethod("reason");

        assertEquals("a", id.invoke(first));
        assertEquals(65, relevance.invoke(first));
        assertEquals("ok", reason.invoke(first));
    }
}
