package com.nhnacademy.search.dto;

public enum BookSortOption {
    RELEVANCE,  // BM25 점수
    NEW,        // 발행일 내림차순 (신상품)
    LOW_PRICE,  // 낮은 가격순
    HIGH_PRICE, // 높은 가격순

    // 나중에 구현할 정렬 기준 (지금 불가능)

    // POPULARITY,
    // RATING,
    // REVIEW_COUNT,
}
