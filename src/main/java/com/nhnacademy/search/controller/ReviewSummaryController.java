package com.nhnacademy.search.controller;

import com.nhnacademy.search.dto.ReviewSummaryResponse;
import com.nhnacademy.search.service.ReviewSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "리뷰 요약 API")
public class ReviewSummaryController {

    private final ReviewSummaryService reviewSummaryService;

    @Operation(summary = "리뷰 요약 조회", description = "ISBN으로 ES에 저장된 리뷰 요약을 조회합니다. 없으면 null일 수 있습니다.")
    @GetMapping("/{isbn}/review-summary")
    public ReviewSummaryResponse reviewSummary(@Parameter(description = "ISBN", example = "9781234567890", required = true) @PathVariable String isbn) {
        String summary = reviewSummaryService.findReviewSummaryByIsbn(isbn);
        return new ReviewSummaryResponse(summary);
    }
}