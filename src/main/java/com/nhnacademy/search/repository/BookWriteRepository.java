package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BookWriteRepository {
    private final JdbcTemplate jdbcTemplate;

    // DB 리뷰 요약 업데이트
    public int updateReviewSummary(long bookId, String summary) {
        return jdbcTemplate.update(
                """
                update Book
                set bookReviewSummary = ?
                where bookId = ?
                """,
                summary, bookId
        );
    }
}
