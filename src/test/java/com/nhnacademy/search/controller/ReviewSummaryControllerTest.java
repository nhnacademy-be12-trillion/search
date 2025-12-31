package com.nhnacademy.search.controller;

import com.nhnacademy.search.service.ReviewSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReviewSummaryController.class)
class ReviewSummaryControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean
    private ReviewSummaryService reviewSummaryService;

    @Test
    void reviewSummary_should_return_json_and_call_service() throws Exception {
        when(reviewSummaryService.findReviewSummaryByIsbn("9781234567890"))
                .thenReturn("좋은 책입니다.");

        mockMvc.perform(get("/review-summary/{isbn}", "9781234567890")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reviewSummary").value("좋은 책입니다."));

        verify(reviewSummaryService).findReviewSummaryByIsbn("9781234567890");
        verifyNoMoreInteractions(reviewSummaryService);
    }

    @Test
    void reviewSummary_should_return_null_when_not_found() throws Exception {
        when(reviewSummaryService.findReviewSummaryByIsbn("9780000000000"))
                .thenReturn(null);

        mockMvc.perform(get("/review-summary/{isbn}", "9780000000000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reviewSummary").value((Object) null));

        verify(reviewSummaryService).findReviewSummaryByIsbn("9780000000000");
        verifyNoMoreInteractions(reviewSummaryService);
    }

    @Test
    void reviewSummary_should_return_empty_string_when_service_returns_empty() throws Exception {
        when(reviewSummaryService.findReviewSummaryByIsbn("9781111111111"))
                .thenReturn("");

        mockMvc.perform(get("/review-summary/{isbn}", "9781111111111")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reviewSummary").value(""));

        verify(reviewSummaryService).findReviewSummaryByIsbn("9781111111111");
        verifyNoMoreInteractions(reviewSummaryService);
    }
}
