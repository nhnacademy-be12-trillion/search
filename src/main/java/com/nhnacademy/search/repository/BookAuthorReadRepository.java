package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class BookAuthorReadRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 한 책(bookId)당 저자 이름들을 ", " 로 묶은 값
     */
    public record BookAuthorsRow(long bookId, String isbn, String authorsCsv) { }

    /**
     * 기존 방식 (bookId > lastBookId 로 페이징)
     */
    public List<BookAuthorsRow> findBookAuthorsAfter(long lastBookId, int limit) {
        String sql = """
                SELECT
                    b.bookId,
                    b.isbn,
                    GROUP_CONCAT(DISTINCT a.authorName ORDER BY a.authorName SEPARATOR ', ') AS authors
                FROM Book b
                LEFT JOIN BookAuthor ba ON ba.book_id = b.bookId
                LEFT JOIN Author a ON a.authorId = ba.author_id
                WHERE b.isbn IS NOT NULL
                  AND b.isbn <> ''
                  AND b.bookId > ?
                GROUP BY b.bookId, b.isbn
                ORDER BY b.bookId
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new BookAuthorsRow(
                        rs.getLong("bookId"),
                        rs.getString("isbn"),
                        rs.getString("authors")
                ),
                lastBookId,
                limit
        );
    }

    /**
     * 이번 batch 의 bookId 들로 저자 목록을 한 번에 가져오는 버전
     * - FullReindex 에서 rows 리스트의 bookId 들을 넘겨서 사용
     */
    public Map<Long, BookAuthorsRow> findByBookIds(List<Long> bookIds) {
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
                    GROUP_CONCAT(DISTINCT a.authorName ORDER BY a.authorName SEPARATOR ', ') AS authors
                FROM Book b
                LEFT JOIN BookAuthor ba ON ba.book_id = b.bookId
                LEFT JOIN Author a ON a.authorId = ba.author_id
                WHERE b.bookId IN (%s)
                GROUP BY b.bookId, b.isbn
                """.formatted(inClause);

        List<BookAuthorsRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new BookAuthorsRow(
                        rs.getLong("bookId"),
                        rs.getString("isbn"),
                        rs.getString("authors")
                ),
                bookIds.toArray()
        );

        Map<Long, BookAuthorsRow> map = new HashMap<>();
        for (BookAuthorsRow row : rows) {
            map.put(row.bookId(), row);
        }
        return map;
    }
}
