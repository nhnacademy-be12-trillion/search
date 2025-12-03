package com.nhnacademy.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pipeline.review-summary")
public class ReviewSummaryPipelineProperties {
    private boolean enabled = false;
    private boolean runOnStartup = true;
    private int recentReviewLimit = 50;
    private int perBookReviewLimit = 30;
}
