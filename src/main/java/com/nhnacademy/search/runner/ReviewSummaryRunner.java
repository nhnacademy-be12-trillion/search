package com.nhnacademy.search.runner;

import com.nhnacademy.search.config.ReviewSummaryPipelineProperties;
import com.nhnacademy.search.service.ReviewSummaryPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(2)
@ConditionalOnProperty(prefix = "pipeline.review-summary", name = "enabled", havingValue = "true")
public class ReviewSummaryRunner implements ApplicationRunner {

    private final ReviewSummaryPipelineService service;
    private final ReviewSummaryPipelineProperties props;

    @Override
    public void run(ApplicationArguments args) {
        service.runOnce(props.getRecentReviewLimit(), props.getPerBookReviewLimit());
        System.out.println("[ReviewSummaryRunner] review summary sync finished.");
    }
}
