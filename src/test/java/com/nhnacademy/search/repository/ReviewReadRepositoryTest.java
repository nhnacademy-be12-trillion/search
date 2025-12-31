package com.nhnacademy.search.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewReadRepositoryTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    ReviewReadRepository repo;

    @Test
    void findRecentReviewBookIds_shouldDistinct_preserveOrder() {
        // given: 최신 순으로 중복 포함
        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<Long>>any(),
                eq(10)
        )).thenReturn(List.of(7L, 7L, 5L, 7L, 2L, 2L));

        // when
        List<Long> out = repo.findRecentReviewBookIds(10);

        // then: stream().distinct()는 "처음 등장 순서" 유지
        assertEquals(List.of(7L, 5L, 2L), out);

        verify(jdbcTemplate).query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), eq(10));
    }

    @Test
    void findRecentReviewBookIds_shouldReturnEmpty_whenNoRows() {
        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<Long>>any(),
                eq(5)
        )).thenReturn(List.of());

        List<Long> out = repo.findRecentReviewBookIds(5);

        assertTrue(out.isEmpty());
    }

    @Test
    void countAllReviewContentsByBookId_shouldReturnCount_whenNotNull() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(99L)
        )).thenReturn(3);

        int out = repo.countAllReviewContentsByBookId(99L);

        assertEquals(3, out);
    }

    @Test
    void countAllReviewContentsByBookId_shouldReturn0_whenNull() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(99L)
        )).thenReturn(null);

        int out = repo.countAllReviewContentsByBookId(99L);

        assertEquals(0, out);
    }

    @Test
    void findRecentReviewContentsByBookId_shouldReturnList() {
        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<String>>any(),
                eq(7L),
                eq(3)
        )).thenReturn(List.of("좋아요", "별로예요"));

        List<String> out = repo.findRecentReviewContentsByBookId(7L, 3);

        assertEquals(List.of("좋아요", "별로예요"), out);

        verify(jdbcTemplate).query(
                anyString(),
                ArgumentMatchers.<RowMapper<String>>any(),
                eq(7L),
                eq(3)
        );
    }
}
