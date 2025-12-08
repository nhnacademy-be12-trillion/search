package com.nhnacademy.search.service;

import com.nhnacademy.search.config.ElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewSummaryService {

    private final WebClient elasticsearchWebClient;
    private final ElasticsearchProperties properties;

    public String findReviewSummaryByIsbn(String isbn) {
        Map<String, Object> esQuery = Map.of(
                "size", 1,
                "_source", List.of("metadata.reviewSummary"),
                "query", Map.of(
                        "bool", Map.of(
                                "should", List.of(
                                        Map.of("term", Map.of("isbn", isbn)),
                                        Map.of("term", Map.of("metadata.isbn.keyword", isbn))
                                ),
                                "minimum_should_match", 1
                        )
                )
        );

        Map<String, Object> esResponse = elasticsearchWebClient.post()
                .uri("/" + properties.getIndex().getBook() + "/_search")
                .bodyValue(esQuery)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        return extractReviewSummary(esResponse);
    }

    @SuppressWarnings("unchecked")
    private String extractReviewSummary(Map<String, Object> esResponse) {
        if (esResponse == null) return null;

        Map<String, Object> hitsRoot = (Map<String, Object>) esResponse.get("hits");
        if (hitsRoot == null) return null;

        List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsRoot.get("hits");
        if (hits == null || hits.isEmpty()) return null;

        Map<String, Object> first = hits.get(0);
        Map<String, Object> source = (Map<String, Object>) first.get("_source");
        if (source == null) return null;

        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        if (metadata == null) return null;

        Object summary = metadata.get("reviewSummary");
        if (summary == null) return null;

        String s = String.valueOf(summary).trim();
        return s.isEmpty() ? null : s;
    }
}

