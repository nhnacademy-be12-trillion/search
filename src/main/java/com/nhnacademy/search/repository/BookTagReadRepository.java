package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // 이번 batch의 bookId들로 태그 한번에 찾기 (단건 조회)
    public Map<Long, BookTagsRow> findByBookIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String inClause = bookIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));

        String sql = """
                SELECT
                    b.bookId,
                    b.isbn,
                    GROUP_CONCAT(DISTINCT t.tag_name ORDER BY t.tag_name SEPARATOR ',') AS tags
                FROM Book b
                LEFT JOIN BookTag bt ON bt.book_id = b.bookId
                LEFT JOIN Tag t ON t.tag_id = bt.tag_id
                WHERE b.bookId IN (%s)
                GROUP BY b.bookId, b.isbn
                """.formatted(inClause);

        List<BookTagsRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new BookTagsRow(
                        rs.getLong("bookId"),
                        rs.getString("isbn"),
                        rs.getString("tags")
                ),
                bookIds.toArray()
        );

        Map<Long, BookTagsRow> map = new HashMap<>();
        for (BookTagsRow row : rows) {
            map.put(row.bookId(), row);
        }
        return map;
    }
}
