package com.nhnacademy.search.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookIsbnReadRepositoryTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    BookIsbnReadRepository repo;

    @Test
    void findIsbnByBookId_returnsTrimmedIsbn_whenFound() {
        long bookId = 1L;

        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<String>>any(),
                eq(bookId)
        )).thenReturn(List.of(" 9781234567890 "));

        Optional<String> out = repo.findIsbnByBookId(bookId);

        assertTrue(out.isPresent());
        assertEquals("9781234567890", out.get());

        verify(jdbcTemplate).query(anyString(), ArgumentMatchers.<RowMapper<String>>any(), eq(bookId));
    }

    @Test
    void findIsbnByBookId_returnsEmpty_whenQueryReturnsEmptyList() {
        long bookId = 2L;

        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<String>>any(),
                eq(bookId)
        )).thenReturn(List.of());

        Optional<String> out = repo.findIsbnByBookId(bookId);

        assertTrue(out.isEmpty());
    }

    @Test
    void findIsbnByBookId_returnsEmpty_whenIsbnIsBlank_afterTrim() {
        long bookId = 3L;

        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<String>>any(),
                eq(bookId)
        )).thenReturn(List.of("   ")); // trim -> "" -> filter로 제거

        Optional<String> out = repo.findIsbnByBookId(bookId);

        assertTrue(out.isEmpty());
    }

    @Test
    void findIsbnByBookId_returnsEmpty_whenIsbnIsEmptyString() {
        long bookId = 4L;

        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<String>>any(),
                eq(bookId)
        )).thenReturn(List.of(""));

        Optional<String> out = repo.findIsbnByBookId(bookId);

        assertTrue(out.isEmpty());
    }
}
