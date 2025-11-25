package com.nhnacademy.search.controller;

import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import com.nhnacademy.search.dto.BookSortOption;
import com.nhnacademy.search.service.BookSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookSearchController {

    private final BookSearchService bookSearchService;

    @GetMapping("/search")
    public BookSearchResponse search(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "RELEVANCE") BookSortOption sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        BookSearchRequest req = new BookSearchRequest(query, sort, page, size);
        return bookSearchService.search(req);
    }
}
