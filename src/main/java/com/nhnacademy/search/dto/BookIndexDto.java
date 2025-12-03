package com.nhnacademy.search.dto;

import java.util.List;

public record BookIndexDto(
        String isbn,
        long bookId,
        List<String> tags,
        long reviewCount,
        double ratingAvg,
        long ratingCount,
        List<String> recentReviewsForSummary
) {}
