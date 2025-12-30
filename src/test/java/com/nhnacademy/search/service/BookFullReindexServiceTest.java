package com.nhnacademy.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.repository.BookAuthorReadRepository;
import com.nhnacademy.search.repository.BookIndexingReadRepository;
import com.nhnacademy.search.repository.BookAuthorReadRepository.BookAuthorsRow;
import com.nhnacademy.search.repository.BookIndexingReadRepository.BookIndexingRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookFullReindexServiceTest {

    @Mock private BookIndexingReadRepository bookIndexingReadRepository;
    @Mock private BookAuthorReadRepository bookAuthorReadRepository;
    @Mock private TagIndexService tagIndexService;
    @Mock private BookStatsSyncService bookStatsSyncService;

    @Mock private ElasticsearchProperties elasticsearchProperties;
    @Mock private WebClient esWebClient;

    @Test
    public void runFullReindex_throws_whenIndexBlank() {
        stubBookIndex("  ");

        BookFullReindexService svc = new BookFullReindexService(
                bookIndexingReadRepository,
                bookAuthorReadRepository,
                tagIndexService,
                bookStatsSyncService,
                elasticsearchProperties,
                esWebClient,
                new ObjectMapper()
        );

        assertThrows(IllegalStateException.class, svc::runFullReindex);
        verifyNoInteractions(bookIndexingReadRepository, bookAuthorReadRepository, tagIndexService, bookStatsSyncService, esWebClient);
    }

    @Test
    public void runFullReindex_callsStepsInOrder() {
        stubBookIndex("books");

        BookFullReindexService real = new BookFullReindexService(
                bookIndexingReadRepository,
                bookAuthorReadRepository,
                tagIndexService,
                bookStatsSyncService,
                elasticsearchProperties,
                esWebClient,
                new ObjectMapper()
        );
        BookFullReindexService svc = spy(real);

        doNothing().when(svc).reindexBaseBooks(eq("books"), eq(500));

        svc.runFullReindex();

        InOrder inOrder = inOrder(svc, tagIndexService, bookStatsSyncService);
        inOrder.verify(svc).reindexBaseBooks("books", 500);
        inOrder.verify(tagIndexService).syncTagsToEs("books", 500);
        inOrder.verify(bookStatsSyncService).runFullSync();
    }

    @Test
    public void reindexBaseBooks_paginates_and_callsBulkTwice() throws Exception {
        // indexName은 reindexBaseBooks 인자로 직접 넣음
        String indexName = "books";

        ObjectMapper om = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // WebClient bulk 체인
        WebClient.RequestBodyUriSpec post = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bulkSpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec bulkHeaders = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec bulkResp = mock(WebClient.ResponseSpec.class);

        when(esWebClient.post()).thenReturn(post);
        when(post.uri(eq("/_bulk"))).thenReturn(bulkSpec);
        when(bulkSpec.contentType(eq(MediaType.APPLICATION_NDJSON))).thenReturn(bulkSpec);
        when(bulkSpec.bodyValue(anyString())).thenReturn(bulkHeaders);
        when(bulkHeaders.retrieve()).thenReturn(bulkResp);
        when(bulkResp.bodyToMono(eq(String.class))).thenReturn(Mono.just("{\"ok\":true}"));

        // page1: bookId 1,2 / page2: bookId 3 / page3: empty
        BookIndexingRow r1 = newRecord(BookIndexingRow.class, Map.of(
                "bookId", 1L,
                "isbn", "isbn-1",
                "bookName", "name1",
                "publisherName", "pub1",
                "bookDescription", "desc1",
                "bookRegularPrice", 10000,
                "bookSalePrice", 9000,
                "imageUrl", "img1",
                "bookPublicationDate", LocalDate.parse("2025-01-01"),
                "bookReviewSummary", "sum1"
        ));
        BookIndexingRow r2 = newRecord(BookIndexingRow.class, Map.of(
                "bookId", 2L,
                "isbn", "isbn-2",
                "bookName", "name2",
                "publisherName", "pub2",
                "bookDescription", "desc2"
        ));
        BookIndexingRow r3 = newRecord(BookIndexingRow.class, Map.of(
                "bookId", 3L,
                "isbn", "isbn-3",
                "bookName", "name3",
                "publisherName", "pub3",
                "bookDescription", "desc3"
        ));

        when(bookIndexingReadRepository.findBooksAfterId(eq(0L), eq(2)))
                .thenReturn(List.of(r1, r2));
        when(bookIndexingReadRepository.findBooksAfterId(eq(2L), eq(2)))
                .thenReturn(List.of(r3));
        when(bookIndexingReadRepository.findBooksAfterId(eq(3L), eq(2)))
                .thenReturn(List.of());

        // authorsMap
        when(bookAuthorReadRepository.findByBookIds(anyList()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<Long> ids = (List<Long>) inv.getArgument(0);
                    Map<Long, BookAuthorsRow> map = new HashMap<>();
                    for (Long id : ids) {
                        map.put(id, newRecord(BookAuthorsRow.class, Map.of(
                                "bookId", id,
                                "isbn", "isbn-" + id,
                                "authorsCsv", "A" + id
                        )));
                    }
                    return map;
                });

        BookFullReindexService svc = new BookFullReindexService(
                bookIndexingReadRepository,
                bookAuthorReadRepository,
                tagIndexService,
                bookStatsSyncService,
                elasticsearchProperties,
                esWebClient,
                om
        );

        svc.reindexBaseBooks(indexName, 2);

        // 호출 흐름
        verify(bookIndexingReadRepository).findBooksAfterId(0L, 2);
        verify(bookIndexingReadRepository).findBooksAfterId(2L, 2);
        verify(bookIndexingReadRepository).findBooksAfterId(3L, 2);

        // bulk 2번
        verify(esWebClient, times(2)).post();
        verify(post, times(2)).uri("/_bulk");
        verify(bulkSpec, times(2)).bodyValue(anyString());
    }

    @Test
    public void reindexBaseBooks_throws_whenObjectMapperSerializeFails() throws Exception {
        String indexName = "books";

        ObjectMapper bad = mock(ObjectMapper.class);
        when(bad.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        BookIndexingRow r1 = newRecord(BookIndexingRow.class, Map.of(
                "bookId", 1L,
                "isbn", "isbn-1",
                "bookName", "name1",
                "publisherName", "pub1",
                "bookDescription", "desc1"
        ));

        when(bookIndexingReadRepository.findBooksAfterId(eq(0L), eq(10))).thenReturn(List.of(r1));

        when(bookAuthorReadRepository.findByBookIds(anyList()))
                .thenReturn(Map.of(1L, newRecord(BookAuthorsRow.class, Map.of("bookId", 1L, "authorsCsv", "A1"))));

        BookFullReindexService svc = new BookFullReindexService(
                bookIndexingReadRepository,
                bookAuthorReadRepository,
                tagIndexService,
                bookStatsSyncService,
                elasticsearchProperties,
                esWebClient,
                bad
        );

        RuntimeException e = assertThrows(RuntimeException.class, () -> svc.reindexBaseBooks(indexName, 10));
        assertTrue(e.getMessage().contains("Failed to serialize"));

        verifyNoInteractions(esWebClient);
    }

    private void stubBookIndex(String value) {
        ElasticsearchProperties.Index idx = mock(ElasticsearchProperties.Index.class);
        when(elasticsearchProperties.getIndex()).thenReturn(idx);
        when(idx.getBook()).thenReturn(value);
    }

    // record 생성(이름 기반)
    private static <T> T newRecord(Class<T> type, Map<String, Object> values) throws Exception {
        if (!type.isRecord()) throw new IllegalArgumentException("Not a record: " + type.getName());

        RecordComponent[] comps = type.getRecordComponents();
        Class<?>[] ptypes = new Class<?>[comps.length];
        Object[] args = new Object[comps.length];

        for (int i = 0; i < comps.length; i++) {
            RecordComponent c = comps[i];
            ptypes[i] = c.getType();

            Object v = values.get(c.getName());
            args[i] = coerceOrDefault(v, ptypes[i]);
        }

        @SuppressWarnings("unchecked")
        Constructor<T> ctor = (Constructor<T>) type.getDeclaredConstructor(ptypes);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    private static Object coerceOrDefault(Object v, Class<?> t) {
        if (v == null) return defaultValue(t);

        if (t.isPrimitive()) {
            if (t == long.class) return ((Number) v).longValue();
            if (t == int.class) return ((Number) v).intValue();
            if (t == double.class) return ((Number) v).doubleValue();
            if (t == float.class) return ((Number) v).floatValue();
            if (t == boolean.class) return (v instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(v));
            if (t == short.class) return ((Number) v).shortValue();
            if (t == byte.class) return ((Number) v).byteValue();
            if (t == char.class) return String.valueOf(v).charAt(0);
        }

        if (Number.class.isAssignableFrom(t) && v instanceof Number n) {
            if (t == Long.class) return n.longValue();
            if (t == Integer.class) return n.intValue();
            if (t == Double.class) return n.doubleValue();
            if (t == Float.class) return n.floatValue();
            if (t == Short.class) return n.shortValue();
            if (t == Byte.class) return n.byteValue();
        }

        if (t == String.class) return String.valueOf(v);
        return v;
    }

    private static Object defaultValue(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == char.class) return '\0';
        return 0;
    }
}
