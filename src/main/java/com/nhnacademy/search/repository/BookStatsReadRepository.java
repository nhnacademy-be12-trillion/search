package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class BookStatsReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public record BookRow(long bookId, String isbn, long viewCount) {}
    public record ReviewAgg(long bookId, int reviewCount, int ratingCount, Double ratingAvg) {}

    // keyset pagination: bookId > lastBookId
    public List<BookRow> findBookPage(long lastBookId, int limit) {
        return jdbcTemplate.query(
                """
                select bookId, isbn, viewCount
                from Book
                where bookId > ?
                order by bookId asc
                limit ?
                """,
                (rs, rn) -> new BookRow(
                        rs.getLong("bookId"),
                        rs.getString("isbn"),
                        rs.getLong("viewCount")
                ),
                lastBookId, limit
        );
    }

    // Review를 book_id 기준으로 집계
    public Map<Long, ReviewAgg> findReviewAggByBookIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) return Map.of();

        String placeholders = bookIds.stream().map(x -> "?").collect(Collectors.joining(","));
        Object[] args = bookIds.toArray();

        List<ReviewAgg> rows = jdbcTemplate.query(
                """
                select
                    book_id as bookId,
                    count(*) as reviewCount,
                    sum(case when reviewRate is not null then 1 else 0 end) as ratingCount,
                    avg(reviewRate) as ratingAvg
                from Review
                where book_id in (""" + placeholders + """
                )
                group by book_id
                """,
                (rs, rn) -> new ReviewAgg(
                        rs.getLong("bookId"),
                        rs.getInt("reviewCount"),
                        rs.getInt("ratingCount"),
                        rs.getObject("ratingAvg", Double.class) // null 가능
                ),
                args
        );

        Map<Long, ReviewAgg> map = new HashMap<>();
        for (ReviewAgg r : rows) map.put(r.bookId(), r);
        return map;
    }

    // viewCount 단건 조회 (popularityScore 계산용)
    public long findViewCountByBookId(long bookId) {
        Long v = jdbcTemplate.queryForObject(
                """
                select viewCount
                from Book
                where bookId = ?
                """,
                Long.class,
                bookId
        );
        return (v == null) ? 0L : v;
    }
}
