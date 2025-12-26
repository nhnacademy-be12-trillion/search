package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewReadRepository {
    private final JdbcTemplate jdbcTemplate;

    // 최근 리뷰들(book_id 중복 허용) -> Java에서 distinct 처리
    public List<Long> findRecentReviewBookIds(int limit) {
        List<Long> rows = jdbcTemplate.query(
                """
                select book_id
                from Review
                order by createdAt desc
                limit ?
                """,
                (rs, rowNum) -> rs.getLong("book_id"),
                limit
        );
        return rows.stream().distinct().toList();
    }

    // 전체 리뷰 개수(유효한 텍스트만)
    public int countAllReviewContentsByBookId(long bookId) {
        Integer c = jdbcTemplate.queryForObject(
                """
                select count(*)
                from Review
                where book_id = ?
                  and reviewContents is not null
                  and trim(reviewContents) <> ''
                """,
                Integer.class,
                bookId
        );
        return (c == null) ? 0 : c;
    }

    public List<String> findRecentReviewContentsByBookId(long bookId, int limit) {
        return jdbcTemplate.query(
                """
                select reviewContents
                from Review
                where book_id = ?
                  and reviewContents is not null
                  and trim(reviewContents) <> ''
                order by createdAt desc
                limit ?
                """,
                (rs, rowNum) -> rs.getString("reviewContents"),
                bookId, limit
        );
    }
}
