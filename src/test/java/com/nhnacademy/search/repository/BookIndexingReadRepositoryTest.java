package com.nhnacademy.search.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookIndexingReadRepositoryTest {

    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks
    BookIndexingReadRepository repo;

    @Test
    void findBooksAfterId_should_map_row_and_convert_pubDate_when_notNull() throws Exception {
        // given
        long lastBookId = 10L;
        int pageSize = 2;

        // ResultSet mock
        ResultSet rs = mock(ResultSet.class);
        when(rs.getDate("bookPublicationDate")).thenReturn(Date.valueOf("1995-12-01"));
        when(rs.getLong("bookId")).thenReturn(11L);
        when(rs.getString("isbn")).thenReturn("97811");
        when(rs.getString("bookName")).thenReturn("자바 맛보기");
        when(rs.getString("bookDescription")).thenReturn("설명...");
        when(rs.getString("imageUrl")).thenReturn("http://img/x.png");
        when(rs.getObject("bookRegularPrice")).thenReturn(12000);
        when(rs.getObject("bookSalePrice")).thenReturn(10800);
        when(rs.getString("publisherName")).thenReturn("씨에이");
        when(rs.getString("bookReviewSummary")).thenReturn("요약...");

        // jdbcTemplate.query(...)가 들어오면 RowMapper를 꺼내서 mapRow를 실제 호출해 리스트를 만들어 반환
        doAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);

            @SuppressWarnings("unchecked")
            RowMapper<BookIndexingReadRepository.BookIndexingRow> mapper =
                    (RowMapper<BookIndexingReadRepository.BookIndexingRow>) inv.getArgument(1);

            long passedLastBookId = inv.getArgument(2, Long.class);
            int passedPageSize = inv.getArgument(3, Integer.class);

            assertEquals(lastBookId, passedLastBookId);
            assertEquals(pageSize, passedPageSize);

            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), anyLong(), anyInt());


        // when
        List<BookIndexingReadRepository.BookIndexingRow> out = repo.findBooksAfterId(lastBookId, pageSize);

        // then
        assertEquals(1, out.size());
        BookIndexingReadRepository.BookIndexingRow row = out.get(0);

        assertEquals(11L, row.bookId());
        assertEquals("97811", row.isbn());
        assertEquals("자바 맛보기", row.bookName());
        assertEquals("설명...", row.bookDescription());
        assertEquals("http://img/x.png", row.imageUrl());
        assertEquals(LocalDate.parse("1995-12-01"), row.bookPublicationDate());
        assertEquals(12000, row.bookRegularPrice());
        assertEquals(10800, row.bookSalePrice());
        assertEquals("씨에이", row.publisherName());
        assertEquals("요약...", row.bookReviewSummary());
    }

    @Test
    void findBooksAfterId_should_map_row_and_keep_pubDate_null_when_null() throws Exception {
        // given
        ResultSet rs = mock(ResultSet.class);
        when(rs.getDate("bookPublicationDate")).thenReturn(null); // ✅ null 분기 커버
        when(rs.getLong("bookId")).thenReturn(12L);
        when(rs.getString("isbn")).thenReturn("97812");
        when(rs.getString("bookName")).thenReturn("테스트");
        when(rs.getString("bookDescription")).thenReturn(null);
        when(rs.getString("imageUrl")).thenReturn(null);
        when(rs.getObject("bookRegularPrice")).thenReturn(null);
        when(rs.getObject("bookSalePrice")).thenReturn(null);
        when(rs.getString("publisherName")).thenReturn(null);
        when(rs.getString("bookReviewSummary")).thenReturn(null);

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            RowMapper<BookIndexingReadRepository.BookIndexingRow> mapper =
                    (RowMapper<BookIndexingReadRepository.BookIndexingRow>) inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        // when
        List<BookIndexingReadRepository.BookIndexingRow> out = repo.findBooksAfterId(0L, 1);

        // then
        assertEquals(1, out.size());
        assertNull(out.get(0).bookPublicationDate());
    }

    @Test
    void findByBookId_should_return_first_when_rows_exist() {
        // given
        long bookId = 99L;

        BookIndexingReadRepository.BookIndexingRow r1 =
                new BookIndexingReadRepository.BookIndexingRow(
                        99L, "97899", "제목1", "desc1", null,
                        LocalDate.parse("2025-01-01"),
                        10000, 9000,
                        "출판사", null
                );
        BookIndexingReadRepository.BookIndexingRow r2 =
                new BookIndexingReadRepository.BookIndexingRow(
                        100L, "978100", "제목2", "desc2", null,
                        null,
                        null, null,
                        null, null
                );

        // query 결과가 여러 개여도 stream().findFirst()로 첫번째만 반환하는지 커버
        doReturn(List.of(r1, r2)).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), aryEq(new Object[]{bookId}));

        // when
        Optional<BookIndexingReadRepository.BookIndexingRow> out = repo.findByBookId(bookId);

        // then
        assertTrue(out.isPresent());
        assertEquals(r1, out.get());
    }

    @Test
    void findByIsbn_should_return_empty_when_no_rows() {
        // given
        String isbn = "978000";
        doReturn(List.of()).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), aryEq(new Object[]{isbn}));

        // when
        Optional<BookIndexingReadRepository.BookIndexingRow> out = repo.findByIsbn(isbn);

        // then
        assertTrue(out.isEmpty());
    }
}
