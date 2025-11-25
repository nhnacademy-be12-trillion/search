package com.nhnacademy.search.dto;

public record BookSearchResult(
        String id,
        String isbn,
        String title,
        String author,
        String publisher,
        Integer price,
        String imageUrl,
        String editionPublishDate,
        Float score // ES _score
) {}
