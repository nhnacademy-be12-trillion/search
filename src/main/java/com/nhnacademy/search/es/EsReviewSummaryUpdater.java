package com.nhnacademy.search.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class EsReviewSummaryUpdater {
    private final WebClient esWebClient;
    private final ObjectMapper objectMapper;

    public EsReviewSummaryUpdater(
            @Qualifier("elasticsearchWebClient") WebClient esWebClient,
            ObjectMapper objectMapper
    ) {
        this.esWebClient = esWebClient;
        this.objectMapper = objectMapper;
    }

    public void updateReviewSummaryByIsbn(String indexName, Map<String, String> isbnToSummary) {
        if (isbnToSummary == null || isbnToSummary.isEmpty()) {
            System.out.println("[EsReviewSummaryUpdater] skip: empty isbnToSummary");
            return;
        }

        Map<String, Object> body = Map.of(
                "query", Map.of("terms", Map.of("metadata.isbn.keyword", isbnToSummary.keySet())),
                "script", Map.of(
                        "lang", "painless",
                        "source", """
                            if (ctx._source.metadata == null) return;
                            def k = ctx._source.metadata.isbn;
                            if (k == null) return;
                            k = k.toString().trim();
                            def v = params.m.get(k);
                            if (v == null) return;
                            ctx._source.metadata.reviewSummary = v;
                        """,
                        "params", Map.of("m", isbnToSummary)
                )
        );

        String resp = esWebClient.post()
                .uri("/{index}/_update_by_query?conflicts=proceed&refresh=true", indexName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        logUbq(indexName, isbnToSummary.size(), resp);
    }

    private void logUbq(String indexName, int keys, String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            System.out.printf(
                    "[EsReviewSummaryUpdater] UBQ index=%s keys=%d took=%dms total=%d updated=%d noops=%d conflicts=%d failures=%d%n",
                    indexName, keys,
                    root.path("took").asLong(-1),
                    root.path("total").asLong(-1),
                    root.path("updated").asLong(-1),
                    root.path("noops").asLong(-1),
                    root.path("version_conflicts").asLong(-1),
                    root.path("failures").isArray() ? root.path("failures").size() : 0
            );
        } catch (Exception e) {
            System.out.printf("[EsReviewSummaryUpdater] parse fail index=%s err=%s%n", indexName, e.getMessage());
        }
    }
}
