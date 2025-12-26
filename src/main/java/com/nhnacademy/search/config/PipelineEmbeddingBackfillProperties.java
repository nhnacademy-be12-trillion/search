package com.nhnacademy.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pipeline.embedding-backfill")
public class PipelineEmbeddingBackfillProperties {
    private boolean enabled = true;
    private boolean runOnStartup = true;

    private int pageSize = 200;

    private int maxDocsPerRun = 1000;

    private String cron = "0 10 3 * * *";
}
