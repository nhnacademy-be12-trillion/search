package com.nhnacademy.search.dto;

public record BookSearchRequest(
        String query,
        BookSortOption sort,
        int page,
        int size
) {
    public int from() {
        return Math.max(page, 0) * Math.max(size, 1);
    }
}
