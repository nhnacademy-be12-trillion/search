package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookTagReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public record BookTagsRow(long bookId, String isbn, String tagsCsv) {}

    public List<BookTagsRow> findBookTagsAfter(long lastBookId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                    Book.bookId,
                    Book.isbn,
                    GROUP_CONCAT(DISTINCT Tag.tag_name ORDER BY Tag.tag_name SEPARATOR ',') AS tags
                FROM Book
                LEFT JOIN BookTag ON BookTag.book_id = Book.bookId
                LEFT JOIN Tag ON Tag.tag_id = BookTag.tag_id
                WHERE Book.isbn IS NOT NULL AND Book.isbn <> ''
                  AND Book.bookId > ?
                GROUP BY Book.bookId, Book.isbn
                ORDER BY Book.bookId
                LIMIT ?
                """,
                (rs, rowNum) -> new BookTagsRow(
                        rs.getLong("bookId"),
                        rs.getString("isbn"),
                        rs.getString("tags")
                ),
                lastBookId, limit
        );
    }
}
