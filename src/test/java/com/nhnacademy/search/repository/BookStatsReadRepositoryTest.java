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
class BookStatsReadRepositoryTest {

    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks
    BookStatsReadRepository repo;

    @Test
    void findBookPage_should_map_rows() throws Exception {
        // given
        long lastBookId = 10L;
        int limit = 2;

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("bookId")).thenReturn(11L);
        when(rs.getString("isbn")).thenReturn("97811");
        when(rs.getLong("viewCount")).thenReturn(123L);

        doAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);
            @SuppressWarnings("unchecked")
            RowMapper<BookStatsReadRepository.BookRow> mapper =
                    (RowMapper<BookStatsReadRepository.BookRow>) inv.getArgument(1);

            long passedLast = inv.getArgument(2, Long.class);
            int passedLimit = inv.getArgument(3, Integer.class);

            assertEquals(lastBookId, passedLast);
            assertEquals(limit, passedLimit);
            assertTrue(sql.contains("from Book"));

            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), anyLong(), anyInt());

        // when
        List<BookStatsReadRepository.BookRow> out = repo.findBookPage(lastBookId, limit);

        // then
        assertEquals(1, out.size());
        BookStatsReadRepository.BookRow row = out.get(0);
        assertEquals(11L, row.bookId());
        assertEquals("97811", row.isbn());
        assertEquals(123L, row.viewCount());
    }

    @Test
    void findReviewAggByBookIds_should_return_empty_when_null_or_empty() {
        assertEquals(Map.of(), repo.findReviewAggByBookIds(null));
        assertEquals(Map.of(), repo.findReviewAggByBookIds(List.of()));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void findReviewAggByBookIds_should_build_map_and_keep_ratingAvg_nullable() throws Exception {
        // given
        List<Long> ids = List.of(1L, 2L, 3L);

        ResultSet rs1 = mock(ResultSet.class);
        when(rs1.getLong("bookId")).thenReturn(1L);
        when(rs1.getInt("reviewCount")).thenReturn(5);
        when(rs1.getInt("ratingCount")).thenReturn(4);
        when(rs1.getObject("ratingAvg", Double.class)).thenReturn(4.25);

        ResultSet rs2 = mock(ResultSet.class);
        when(rs2.getLong("bookId")).thenReturn(3L);
        when(rs2.getInt("reviewCount")).thenReturn(2);
        when(rs2.getInt("ratingCount")).thenReturn(0);
        when(rs2.getObject("ratingAvg", Double.class)).thenReturn(null); // ✅ null 가능 분기

        doAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);

            // placeholders 검증: "?,?,?" 형태
            assertTrue(sql.contains("where book_id in (?,?,?)"));

            @SuppressWarnings("unchecked")
            RowMapper<BookStatsReadRepository.ReviewAgg> mapper =
                    (RowMapper<BookStatsReadRepository.ReviewAgg>) inv.getArgument(1);

            // varargs는 개별 인자로 들어오므로 2번째부터가 각 bookId
            Object a0 = inv.getArgument(2);
            Object a1 = inv.getArgument(3);
            Object a2 = inv.getArgument(4);
            assertEquals(1L, a0);
            assertEquals(2L, a1);
            assertEquals(3L, a2);

            return List.of(
                    mapper.mapRow(rs1, 0),
                    mapper.mapRow(rs2, 1)
            );
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(), any(), any());

        // when
        Map<Long, BookStatsReadRepository.ReviewAgg> out = repo.findReviewAggByBookIds(ids);

        // then
        assertEquals(2, out.size());

        var r1 = out.get(1L);
        assertNotNull(r1);
        assertEquals(1L, r1.bookId());
        assertEquals(5, r1.reviewCount());
        assertEquals(4, r1.ratingCount());
        assertEquals(4.25, r1.ratingAvg());

        var r3 = out.get(3L);
        assertNotNull(r3);
        assertEquals(3L, r3.bookId());
        assertEquals(2, r3.reviewCount());
        assertEquals(0, r3.ratingCount());
        assertNull(r3.ratingAvg()); // ✅ null 그대로
    }

    @Test
    void findViewCountByBookId_should_return_value_when_notNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(55L);

        long out = repo.findViewCountByBookId(7L);

        assertEquals(55L, out);
    }

    @Test
    void findViewCountByBookId_should_return_0_when_null() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(null);

        long out = repo.findViewCountByBookId(7L);

        assertEquals(0L, out);
    }
}
