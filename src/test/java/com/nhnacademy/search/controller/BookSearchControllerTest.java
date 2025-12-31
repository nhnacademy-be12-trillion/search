package com.nhnacademy.search.controller;

import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import com.nhnacademy.search.dto.BookSortOption;
import com.nhnacademy.search.service.BookAiSearchService;
import com.nhnacademy.search.service.BookSearchService;
import com.nhnacademy.search.service.BookSingleIndexService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BookSearchController.class)
class BookSearchControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean
    private BookSearchService bookSearchService;
    @MockitoBean
    private BookAiSearchService bookAiSearchService;
    @MockitoBean
    private BookSingleIndexService bookSingleIndexService;

    @Test
    void search_should_call_service_with_all_params() throws Exception {
        // given
        BookSearchResponse stub = new BookSearchResponse(List.of(), 0L, 1, 5);
        when(bookSearchService.search(any(BookSearchRequest.class))).thenReturn(stub);

        // when / then
        mockMvc.perform(get("/search")
                        .param("query", "한강")
                        .param("sort", "NEW")
                        .param("page", "1")
                        .param("size", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        ArgumentCaptor<BookSearchRequest> cap = ArgumentCaptor.forClass(BookSearchRequest.class);
        verify(bookSearchService).search(cap.capture());

        BookSearchRequest req = cap.getValue();
        assertEquals("한강", req.query());
        assertEquals(BookSortOption.NEW, req.sort());
        assertEquals(1, req.page());
        assertEquals(5, req.size());
    }

    @Test
    void search_should_apply_defaults_when_params_missing() throws Exception {
        // given: page=0, size=20, sort=RELEVANCE 기본값
        BookSearchResponse stub = new BookSearchResponse(List.of(), 0L, 0, 20);
        when(bookSearchService.search(any(BookSearchRequest.class))).thenReturn(stub);

        // when / then
        mockMvc.perform(get("/search")
                        .param("query", "스프링")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        ArgumentCaptor<BookSearchRequest> cap = ArgumentCaptor.forClass(BookSearchRequest.class);
        verify(bookSearchService).search(cap.capture());

        BookSearchRequest req = cap.getValue();
        assertEquals("스프링", req.query());
        assertEquals(BookSortOption.RELEVANCE, req.sort());
        assertEquals(0, req.page());
        assertEquals(20, req.size());
    }

    @Test
    void search_should_return_400_when_query_missing() throws Exception {
        mockMvc.perform(get("/search")
                        .param("sort", "RELEVANCE"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookSearchService);
    }

    @Test
    void aiSearch_should_call_ai_service() throws Exception {
        // given
        BookSearchResponse stub = new BookSearchResponse(List.of(), 0L, 0, 20);
        when(bookAiSearchService.search(any(BookSearchRequest.class))).thenReturn(stub);

        // when / then
        mockMvc.perform(get("/search/ai")
                        .param("query", "한강")
                        .param("sort", "RELEVANCE")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        ArgumentCaptor<BookSearchRequest> cap = ArgumentCaptor.forClass(BookSearchRequest.class);
        verify(bookAiSearchService).search(cap.capture());

        BookSearchRequest req = cap.getValue();
        assertEquals("한강", req.query());
        assertEquals(BookSortOption.RELEVANCE, req.sort());
        assertEquals(0, req.page());
        assertEquals(20, req.size());
    }

    @Test
    void upsertByIsbn_should_return_204_and_call_service() throws Exception {
        mockMvc.perform(post("/search/index/isbn/{isbn}", "9788986604009"))
                .andExpect(status().isNoContent());

        verify(bookSingleIndexService).upsertByIsbnWithRetry("9788986604009");
        verifyNoMoreInteractions(bookSingleIndexService);
    }

    @Test
    void upsertByBookId_should_return_204_and_call_service() throws Exception {
        mockMvc.perform(post("/search/index/{bookId}", 5981L))
                .andExpect(status().isNoContent());

        verify(bookSingleIndexService).upsertByBookId(5981L);
        verifyNoMoreInteractions(bookSingleIndexService);
    }

    @Test
    void deleteByBookId_should_return_204_and_call_service() throws Exception {
        mockMvc.perform(delete("/search/index/{bookId}", 5981L))
                .andExpect(status().isNoContent());

        verify(bookSingleIndexService).deleteByBookId(5981L);
        verifyNoMoreInteractions(bookSingleIndexService);
    }

    @Test
    void search_should_return_400_when_sort_invalid() throws Exception {
        mockMvc.perform(get("/search")
                        .param("query", "한강")
                        .param("sort", "NOT_A_SORT"))
                .andExpect(status().isBadRequest());
    }
}
