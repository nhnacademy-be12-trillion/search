package com.nhnacademy.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiSearchProperties {

    private Ollama ollama = new Ollama();
    private Reranker reranker = new Reranker();
    private Search search = new Search();

    @Getter @Setter
    public static class Ollama {
        private String baseUrl;
        private String embedModel = "bge-m3";
        private Duration timeout = Duration.ofSeconds(10);
    }

    @Getter @Setter
    public static class Reranker {
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(10);
    }

    @Getter @Setter
    public static class Search {
        private String embeddingField = "embedding";
        private String scriptSource = "cosineSimilarity(params.qv, 'embedding') + 1.0";

        private int minCandidates = 50;
        private int maxCandidates = 200;
        private int candidateMultiplier = 5;

        private int rerankTextMaxLen = 1200;
    }
}
