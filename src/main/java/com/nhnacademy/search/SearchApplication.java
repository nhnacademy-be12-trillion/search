package com.nhnacademy.search;

import com.nhnacademy.search.config.BookStatsSyncProperties;
import com.nhnacademy.search.config.ElasticsearchProperties;
import com.nhnacademy.search.config.GeminiProperties;
import com.nhnacademy.search.config.ReviewSummaryPipelineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ElasticsearchProperties.class, GeminiProperties.class, ReviewSummaryPipelineProperties.class, BookStatsSyncProperties.class})
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }

}
