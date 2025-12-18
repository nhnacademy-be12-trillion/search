// src/main/java/com/nhnacademy/search/runner/BookFullReindexRunner.java
package com.nhnacademy.search.runner;

import com.nhnacademy.search.service.BookFullReindexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
@ConditionalOnProperty(
        prefix = "pipeline.full-reindex",
        name = "enabled",
        havingValue = "true"
)
public class BookFullReindexRunner implements ApplicationRunner {

    private final BookFullReindexService bookFullReindexService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[BookFullReindexRunner] START full reindex");
        bookFullReindexService.runFullReindex();
        log.info("[BookFullReindexRunner] END full reindex");
    }
}
