package com.nhnacademy.search;

import com.nhnacademy.search.schedule.EmbeddingBackfillScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class SearchApplicationTests {
    @MockitoBean
    private WebClient elasticsearchWebClient;
    @MockitoBean
    private EmbeddingBackfillScheduler scheduler;
    @Test
    void contextLoads() {

    }
}
