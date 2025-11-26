package com.nhnacademy.search.dto;

public enum BookSortOption {
    RELEVANCE,      // 정확도
    NEW,            // 발행일 내림차순 (신상품)
    LOW_PRICE,      // 낮은 가격순
    HIGH_PRICE,     // 높은 가격순


    // 나중에 구현할 정렬 기준 (미구현)
     POPULARITY,    // 인기도
     RATING,        // 평점
     REVIEW_COUNT,  // 리뷰 많은 순
}
