package com.nhnacademy.search.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookTagReadRepositoryTest {

    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks
    BookTagReadRepository repo;

    @Test
    void findBookTagsAfter_should_map_rows_and_pass_params() throws Exception {
        // given
        long lastBookId = 10L;
        int limit = 2;

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("bookId")).thenReturn(11L);
        when(rs.getString("isbn")).thenReturn("97811");
        when(rs.getString("tags")).thenReturn("자바,스프링");

        doAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);

            @SuppressWarnings("unchecked")
            RowMapper<BookTagReadRepository.BookTagsRow> mapper =
                    (RowMapper<BookTagReadRepository.BookTagsRow>) inv.getArgument(1);

            long passedLast = inv.getArgument(2, Long.class);
            int passedLimit = inv.getArgument(3, Integer.class);

            assertEquals(lastBookId, passedLast);
            assertEquals(limit, passedLimit);
            assertTrue(sql.contains("GROUP_CONCAT"));
            assertTrue(sql.contains("LIMIT ?"));

            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), anyLong(), anyInt());

        // when
        List<BookTagReadRepository.BookTagsRow> out = repo.findBookTagsAfter(lastBookId, limit);

        // then
        assertEquals(1, out.size());
        var row = out.get(0);
        assertEquals(11L, row.bookId());
        assertEquals("97811", row.isbn());
        assertEquals("자바,스프링", row.tagsCsv());
    }

    @Test
    void findByBookIds_should_return_empty_when_null_or_empty() {
        assertEquals(Map.of(), repo.findByBookIds(null));
        assertEquals(Map.of(), repo.findByBookIds(List.of()));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void findByBookIds_should_build_in_clause_and_return_map() throws Exception {
        // given
        List<Long> ids = List.of(1L, 2L, 3L);

        ResultSet rs1 = mock(ResultSet.class);
        when(rs1.getLong("bookId")).thenReturn(1L);
        when(rs1.getString("isbn")).thenReturn("9781");
        when(rs1.getString("tags")).thenReturn("a,b");

        ResultSet rs2 = mock(ResultSet.class);
        when(rs2.getLong("bookId")).thenReturn(3L);
        when(rs2.getString("isbn")).thenReturn("9783");
        when(rs2.getString("tags")).thenReturn(null); // tags null 가능

        doAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);

            // placeholders가 "?, ?, ?" 형태로 들어가야 함
            assertTrue(sql.contains("WHERE b.bookId IN (?, ?, ?)"));

            @SuppressWarnings("unchecked")
            RowMapper<BookTagReadRepository.BookTagsRow> mapper =
                    (RowMapper<BookTagReadRepository.BookTagsRow>) inv.getArgument(1);

            assertEquals(1L, ((Long) inv.getArgument(2)).longValue());
            assertEquals(2L, ((Long) inv.getArgument(3)).longValue());
            assertEquals(3L, ((Long) inv.getArgument(4)).longValue());

            return List.of(
                    mapper.mapRow(rs1, 0),
                    mapper.mapRow(rs2, 1)
            );
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(), any(), any());

        // when
        Map<Long, BookTagReadRepository.BookTagsRow> out = repo.findByBookIds(ids);

        // then
        assertEquals(2, out.size());

        var r1 = out.get(1L);
        assertNotNull(r1);
        assertEquals(1L, r1.bookId());
        assertEquals("9781", r1.isbn());
        assertEquals("a,b", r1.tagsCsv());

        var r3 = out.get(3L);
        assertNotNull(r3);
        assertEquals(3L, r3.bookId());
        assertEquals("9783", r3.isbn());
        assertNull(r3.tagsCsv());
    }
}
