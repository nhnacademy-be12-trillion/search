package com.nhnacademy.search.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookAuthorReadRepositoryTest {

    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks
    BookAuthorReadRepository repo;

    @Test
    void findBookAuthorsAfter_should_call_jdbcTemplate_with_args_and_return_rows() {
        // given
        long lastBookId = 10L;
        int limit = 3;

        List<BookAuthorReadRepository.BookAuthorsRow> stub = List.of(
                new BookAuthorReadRepository.BookAuthorsRow(11L, "97811", "A, B"),
                new BookAuthorReadRepository.BookAuthorsRow(12L, "97812", null)
        );

        // varargs(Object...)는 Mockito에서 Object[]로 매칭
        doReturn(stub).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);

        // when
        List<BookAuthorReadRepository.BookAuthorsRow> out = repo.findBookAuthorsAfter(lastBookId, limit);

        // then
        assertSame(stub, out);

        verify(jdbcTemplate).query(sqlCap.capture(), any(RowMapper.class), argsCap.capture());

        String sql = sqlCap.getValue();
        assertTrue(sql.contains("GROUP_CONCAT"), "sql should contain GROUP_CONCAT");
        assertTrue(sql.contains("LIMIT ?"), "sql should contain LIMIT ?");

        Object[] args = argsCap.getValue();
        assertArrayEquals(new Object[]{lastBookId, limit}, args);
    }

    @Test
    void findByBookIds_should_return_emptyMap_when_null_or_empty() {
        assertEquals(Collections.emptyMap(), repo.findByBookIds(null));
        assertEquals(Collections.emptyMap(), repo.findByBookIds(List.of()));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void findByBookIds_should_build_in_clause_and_map_rows_by_bookId() {
        // given
        List<Long> ids = List.of(1L, 2L, 3L);

        List<BookAuthorReadRepository.BookAuthorsRow> stubRows = List.of(
                new BookAuthorReadRepository.BookAuthorsRow(1L, "9781", "A"),
                new BookAuthorReadRepository.BookAuthorsRow(3L, "9783", "C, D")
        );

        doReturn(stubRows).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);

        // when
        Map<Long, BookAuthorReadRepository.BookAuthorsRow> out = repo.findByBookIds(ids);

        // then
        verify(jdbcTemplate).query(sqlCap.capture(), any(RowMapper.class), argsCap.capture());

        String sql = sqlCap.getValue();
        // ? 개수가 ids.size()와 동일해야 함
        long qCount = sql.chars().filter(ch -> ch == '?').count();
        assertEquals(ids.size(), qCount, "IN clause placeholders should match ids size");

        Object[] args = argsCap.getValue();
        assertArrayEquals(ids.toArray(), args);

        assertEquals(2, out.size());
        assertEquals("9781", out.get(1L).isbn());
        assertEquals("C, D", out.get(3L).authorsCsv());
        assertNull(out.get(2L));
    }
}
