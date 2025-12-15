package com.nhnacademy.search.controller;

import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import com.nhnacademy.search.dto.BookSortOption;
import com.nhnacademy.search.service.BookAiSearchService;
import com.nhnacademy.search.service.BookSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Books", description = "도서 검색 API")
public class BookSearchController {

    private final BookSearchService bookSearchService;
    private final BookAiSearchService bookAiSearchService;

    @Operation(
            summary = "도서 검색",
            description = "query를 기반으로 검색하고 sort/page/size로 정렬 및 페이징합니다."
    )
    @GetMapping
    public BookSearchResponse search(
            @Parameter(description = "검색어", example = "한강", required = true)
            @RequestParam String query,

            @Parameter(description = "정렬 기준 (기본: RELEVANCE)", example = "NEW")
            @RequestParam(required = false, defaultValue = "RELEVANCE") BookSortOption sort,

            @Parameter(description = "페이지(0부터 시작)", example = "0")
            @RequestParam(required = false, defaultValue = "0") int page,

            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        BookSearchRequest req = new BookSearchRequest(query, sort, page, size);
        return bookSearchService.search(req);
    }

    @Operation(summary = "AI 도서 검색", description = "Vector 검색 + Rerank + Gemini 검증/추천이유까지 수행합니다.")
    @GetMapping("/ai")
    public BookSearchResponse aiSearch(
            @Parameter(description = "검색어", example = "한강", required = true)
            @RequestParam String query,
            @Parameter(description = "정렬 기준 (기본: RELEVANCE)", example = "RELEVANCE")
            @RequestParam(required = false, defaultValue = "RELEVANCE") BookSortOption sort,
            @Parameter(description = "페이지(0부터 시작)", example = "0")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return bookAiSearchService.search(new BookSearchRequest(query, sort, page, size));
    }
}
