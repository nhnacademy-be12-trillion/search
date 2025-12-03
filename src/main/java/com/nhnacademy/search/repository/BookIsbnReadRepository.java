package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookIsbnReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public Optional<String> findIsbnByBookId(long bookId) {
        return jdbcTemplate.query(
                """
                select isbn
                from Book
                where bookId = ?
                  and isbn is not null
                  and trim(isbn) <> ''
                """,
                (rs, rowNum) -> rs.getString("isbn"),
                bookId
        ).stream().findFirst().map(String::trim).filter(s -> !s.isBlank());
    }
}
