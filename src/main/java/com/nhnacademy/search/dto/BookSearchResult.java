package com.nhnacademy.search.dto;

import java.util.List;

public record BookSearchResult(
        String id,
        String isbn,
        String title,
        String subtitle,
        String author,
        String publisher,
        Integer price,
        String imageUrl,
        String editionPublishDate,
        List<String> tags,
        Float ratingAvg,
        Integer reviewCount,
        Float score // ES _score
) {}
