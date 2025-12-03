package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewDeltaRepository {
    private final JdbcTemplate jdbcTemplate;

    public record BookRow(long bookId, String isbn) {}

    public List<BookRow> findBooksFromRecentReviews(int recentReviewLimit) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT b.book_id AS bookId, b.isbn AS isbn
                FROM (
                  SELECT book_id
                  FROM Review
                  ORDER BY createdAt DESC
                  LIMIT ?
                ) r
                JOIN Book b ON b.book_id = r.book_id
                WHERE b.book_state <> 'DELETED'
                  AND b.isbn IS NOT NULL AND b.isbn <> ''
                """,
                (rs, rowNum) -> new BookRow(rs.getLong("bookId"), rs.getString("isbn")),
                recentReviewLimit
        );
    }

    public List<String> findRecentReviewContents(long bookId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT reviewContents
                FROM Review
                WHERE book_id = ?
                ORDER BY createdAt DESC
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getString("reviewContents"),
                bookId, limit
        );
    }
}

