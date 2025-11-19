package com.nhnacademy.search.dto;

public record BookSearchResult(
        long id,
        String isbn,
        String title,
        String author,
        String publisher,
        int price,
        String imageUrl,
        String editionPublishDate,
        float score // ES _score
) {}
