package com.nhnacademy.search.service;

import com.nhnacademy.search.es.EsTagUpdater;
import com.nhnacademy.search.repository.BookTagReadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TagIndexServiceTest {

    @Mock private BookTagReadRepository repo;
    @Mock private EsTagUpdater esTagUpdater;

    @Test
    public void syncTagsToEs_breaks_whenEmpty() {
        when(repo.findBookTagsAfter(anyLong(), anyInt())).thenReturn(List.of());

        TagIndexService svc = new TagIndexService(repo, esTagUpdater);
        svc.syncTagsToEs("books", 10);

        verifyNoInteractions(esTagUpdater);
    }

    @Test
    public void syncTagsToEs_skips_whenIsbnBlank() throws Exception {
        Class<?> rowType = tagRowType();

        Object row = newRecord(rowType, Map.of(
                "bookId", 10L,
                "isbn", "  ",
                "tagsCsv", "a,b"
        ));

        when(repo.findBookTagsAfter(eq(0L), eq(10))).thenReturn((List) List.of(row));
        when(repo.findBookTagsAfter(eq(10L), eq(10))).thenReturn(List.of());

        TagIndexService svc = new TagIndexService(repo, esTagUpdater);
        svc.syncTagsToEs("books", 10);

        verifyNoInteractions(esTagUpdater);
    }

    @Test
    public void syncTagsToEs_trims_tags_and_callsUpdater() throws Exception {
        Class<?> rowType = tagRowType();

        Object r1 = newRecord(rowType, Map.of(
                "bookId", 10L,
                "isbn", "isbn-10",
                "tagsCsv", " a, b , ,  c  ,"
        ));
        Object r2 = newRecord(rowType, Map.of(
                "bookId", 12L,
                "isbn", "isbn-12",
                "tagsCsv", ""
        ));

        when(repo.findBookTagsAfter(eq(0L), eq(10))).thenReturn((List) List.of(r1, r2));
        when(repo.findBookTagsAfter(eq(12L), eq(10))).thenReturn(List.of());

        TagIndexService svc = new TagIndexService(repo, esTagUpdater);
        svc.syncTagsToEs("books", 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> cap = ArgumentCaptor.forClass(Map.class);

        verify(esTagUpdater).updateMetadataTagsByIsbn(eq("books"), cap.capture());
        Map<String, List<String>> isbnToTags = cap.getValue();

        assertEquals(List.of("a", "b", "c"), isbnToTags.get("isbn-10"));
        assertEquals(List.of(), isbnToTags.get("isbn-12"));
    }

    // repo 메서드의 제네릭에서 Row 타입 추출
    private static Class<?> tagRowType() throws Exception {
        Method m = BookTagReadRepository.class.getMethod("findBookTagsAfter", long.class, int.class);
        Type rt = m.getGenericReturnType();
        if (rt instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) return c;
        }
        throw new IllegalStateException("Cannot resolve tag row type from BookTagReadRepository");
    }

    private static Object newRecord(Class<?> type, Map<String, Object> values) throws Exception {
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

        Constructor<?> ctor = type.getDeclaredConstructor(ptypes);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    private static Object coerceOrDefault(Object v, Class<?> t) {
        if (v == null) return defaultValue(t);

        if (t.isPrimitive()) {
            if (t == long.class) return ((Number) v).longValue();
            if (t == int.class) return ((Number) v).intValue();
            if (t == boolean.class) return (v instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(v));
            return 0;
        }

        if (t == String.class) return String.valueOf(v);
        if (t == Long.class && v instanceof Number n) return n.longValue();
        if (t == Integer.class && v instanceof Number n) return n.intValue();

        return v;
    }

    private static Object defaultValue(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        return 0;
    }
}
