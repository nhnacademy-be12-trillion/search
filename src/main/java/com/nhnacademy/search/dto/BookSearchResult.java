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
        Integer salePrice,
        String imageUrl,
        String editionPublishDate,
        List<String> tags,
        Float ratingAvg,
        Integer reviewCount,
        Float score, // ES _score

        Integer relevancePercent,        // 0~100
        String recommendationReason,     // 추천 이유(1~2문장)
        Boolean recommended              // 추천 배지
) {}
