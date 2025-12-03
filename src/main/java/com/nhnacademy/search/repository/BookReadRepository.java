package com.nhnacademy.search.repository;

import com.nhnacademy.search.dto.ReviewStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<Long> findAllBookIds() {
        return jdbcTemplate.query(
                "select book_id from Book where book_state <> 'DELETED'",
                (rs, rowNum) -> rs.getLong("book_id")
        );
    }

    public List<String> findTagNamesByBookId(long bookId) {
        return jdbcTemplate.query(
                """
                select t.tag_name
                from BookTag bt
                join Tag t on t.tag_id = bt.tag_id
                where bt.book_id = ?
                """,
                (rs, rowNum) -> rs.getString("tag_name"),
                bookId
        );
    }

    public ReviewStatsDto findReviewStatsByBookId(long bookId) {
        return jdbcTemplate.queryForObject(
                """
                select
                    count(*) as reviewCount,
                    coalesce(avg(reviewRate), 0) as ratingAvg
                from Review
                where book_id = ?
                """,
                (rs, rowNum) -> {
                    long cnt = rs.getLong("reviewCount");
                    double avg = rs.getDouble("ratingAvg");
                    return new ReviewStatsDto(cnt, avg, cnt); // ratingCount = 보통 리뷰 수
                },
                bookId
        );
    }

    public List<String> findRecentReviewContents(long bookId, int limit) {
        return jdbcTemplate.query(
                """
                select reviewContents
                from Review
                where book_id = ?
                order by createdAt desc
                limit ?
                """,
                (rs, rowNum) -> rs.getString("reviewContents"),
                bookId, limit
        );
    }
}
