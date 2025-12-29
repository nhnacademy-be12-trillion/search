// src/main/java/com/nhnacademy/search/repository/BookIndexingReadRepository.java
package com.nhnacademy.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookIndexingReadRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * ES 인덱스의 "베이스 도큐먼트"를 만들 때 필요한 최소 필드 모음
     *
     * - id                -> Book.bookId
     * - isbn              -> Book.isbn
     * - title             -> Book.bookName
     * - publisherName     -> Publisher.publisherName
     * - bookContent       -> bookDescription
     * - metadata.isbn     -> isbn
     * - metadata.title    -> bookName
     * - metadata.publisher-> publisherName
     * - metadata.price    -> bookRegularPrice
     * - metadata.salePrice-> bookSalePrice
     * - metadata.imageUrl -> BookFile.fileUrl (joinedId = bookId)
     * - metadata.editionPublishDate -> bookPublicationDate
     * - metadata.reviewSummary      -> bookReviewSummary
     */
    public record BookIndexingRow(
            long bookId,
            String isbn,
            String bookName,
            String bookDescription,
            String imageUrl,
            LocalDate bookPublicationDate,
            Integer bookRegularPrice,
            Integer bookSalePrice,
            String publisherName,
            String bookReviewSummary
    ) {
    }

    /**
     * lastBookId 이후의 책들을 pageSize 만큼 가져온다.
     * 한 번에 모든 조인을 끝내서 ES 쪽으로는 BookIndexingRow 만 넘기면 되도록 설계.
     */
    public List<BookIndexingRow> findBooksAfterId(long lastBookId, int pageSize) {
        String sql = """
            SELECT
                b.bookId,
                b.isbn,
                b.bookName,
                b.bookDescription,
                (
                    SELECT bf.fileUrl
                    FROM BookFile bf
                    WHERE bf.joinedId = b.bookId
                    ORDER BY bf.fileId ASC
                    LIMIT 1
                ) AS imageUrl,
                b.bookPublicationDate,
                b.bookRegularPrice,
                b.bookSalePrice,
                p.publisherName,
                b.bookReviewSummary
            FROM Book b
            LEFT JOIN Publisher p
              ON p.publisherId = b.publisher_publisherId
            WHERE b.bookId > ?
            ORDER BY b.bookId ASC
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Date pubDate = rs.getDate("bookPublicationDate");
                    LocalDate publicationDate =
                            (pubDate != null) ? pubDate.toLocalDate() : null;

                    return new BookIndexingRow(
                            rs.getLong("bookId"),
                            rs.getString("isbn"),
                            rs.getString("bookName"),
                            rs.getString("bookDescription"),   // <- bookIndex 안 씀
                            rs.getString("imageUrl"),
                            publicationDate,
                            (Integer) rs.getObject("bookRegularPrice"),
                            (Integer) rs.getObject("bookSalePrice"),
                            rs.getString("publisherName"),
                            rs.getString("bookReviewSummary")
                    );
                },
                lastBookId,
                pageSize
        );
    }

    // bookId 단건 조회
    public Optional<BookIndexingRow> findByBookId(long bookId) {
        String sql = """
            SELECT
                b.bookId,
                b.isbn,
                b.bookName,
                b.bookDescription,
                (
                    SELECT bf.fileUrl
                    FROM BookFile bf
                    WHERE bf.joinedId = b.bookId
                    ORDER BY bf.fileId ASC
                    LIMIT 1
                ) AS imageUrl,
                b.bookPublicationDate,
                b.bookRegularPrice,
                b.bookSalePrice,
                p.publisherName,
                b.bookReviewSummary
            FROM Book b
            LEFT JOIN Publisher p
              ON p.publisherId = b.publisher_publisherId
            WHERE b.bookId = ?
            LIMIT 1
            """;

        List<BookIndexingRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Date pubDate = rs.getDate("bookPublicationDate");
                    LocalDate publicationDate =
                            (pubDate != null) ? pubDate.toLocalDate() : null;

                    return new BookIndexingRow(
                            rs.getLong("bookId"),
                            rs.getString("isbn"),
                            rs.getString("bookName"),
                            rs.getString("bookDescription"),
                            rs.getString("imageUrl"),
                            publicationDate,
                            (Integer) rs.getObject("bookRegularPrice"),
                            (Integer) rs.getObject("bookSalePrice"),
                            rs.getString("publisherName"),
                            rs.getString("bookReviewSummary")
                    );
                },
                bookId
        );

        return rows.stream().findFirst();
    }

    // isbn 단건 조회
    public Optional<BookIndexingRow> findByIsbn(String isbn) {
        String sql = """
            SELECT
                b.bookId,
                b.isbn,
                b.bookName,
                b.bookDescription,
                (
                    SELECT bf.fileUrl
                    FROM BookFile bf
                    WHERE bf.joinedId = b.bookId
                    ORDER BY bf.fileId ASC
                    LIMIT 1
                ) AS imageUrl,
                b.bookPublicationDate,
                b.bookRegularPrice,
                b.bookSalePrice,
                p.publisherName,
                b.bookReviewSummary
            FROM Book b
            LEFT JOIN Publisher p
              ON p.publisherId = b.publisher_publisherId
            WHERE b.isbn = ?
            ORDER BY b.bookId DESC
            LIMIT 1
            """;

        List<BookIndexingRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Date pubDate = rs.getDate("bookPublicationDate");
                    LocalDate publicationDate =
                            (pubDate != null) ? pubDate.toLocalDate() : null;

                    return new BookIndexingRow(
                            rs.getLong("bookId"),
                            rs.getString("isbn"),
                            rs.getString("bookName"),
                            rs.getString("bookDescription"),
                            rs.getString("imageUrl"),
                            publicationDate,
                            (Integer) rs.getObject("bookRegularPrice"),
                            (Integer) rs.getObject("bookSalePrice"),
                            rs.getString("publisherName"),
                            rs.getString("bookReviewSummary")
                    );
                },
                isbn
        );

        return rows.stream().findFirst();
    }
}