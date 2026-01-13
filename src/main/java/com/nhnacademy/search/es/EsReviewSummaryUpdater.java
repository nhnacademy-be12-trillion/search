package com.nhnacademy.search.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
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
            log.info("[EsReviewSummaryUpdater] skip: empty isbnToSummary");
            return;
        }

        var keys = isbnToSummary.keySet();

        Map<String, Object> body = Map.of(
                "query", Map.of(
                        "bool", Map.of(
                                "should", List.of(
                                        Map.of("terms", Map.of("metadata.isbn", keys)),
                                        Map.of("terms", Map.of("isbn", keys))
                                ),
                                "minimum_should_match", 1
                        )
                ),
                "script", Map.of(
                        "lang", "painless",
                        "source", """
            def isbn = null;
            if (ctx._source.metadata != null && ctx._source.metadata.isbn != null) {
                isbn = ctx._source.metadata.isbn.toString().trim();
            } else if (ctx._source.isbn != null) {
                isbn = ctx._source.isbn.toString().trim();
            }
            if (isbn == null) return;

            def v = params.m.get(isbn);
            if (v == null) return;

            if (ctx._source.metadata == null) ctx._source.metadata = new HashMap();
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
        if (resp == null || resp.isBlank()) {
            log.warn("[EsReviewSummaryUpdater] UBQ response empty. index={} keys={}", indexName, keys);
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(resp);

            long took = root.path("took").asLong(-1);
            long total = root.path("total").asLong(-1);
            long updated = root.path("updated").asLong(-1);
            long noops = root.path("noops").asLong(-1);
            long conflicts = root.path("version_conflicts").asLong(-1);

            JsonNode failures = root.path("failures");
            int failureCount = (failures != null && failures.isArray()) ? failures.size() : 0;

            log.info("[EsReviewSummaryUpdater] UBQ index={} keys={} took={}ms total={} updated={} noops={} conflicts={} failures={}",
                    indexName, keys, took, total, updated, noops, conflicts, failureCount);
        } catch (Exception e) {
            log.error("[EsReviewSummaryUpdater] parse fail index={} keys={}", indexName, keys, e);
        }
    }
}
