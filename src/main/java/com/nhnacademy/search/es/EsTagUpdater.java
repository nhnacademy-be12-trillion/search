package com.nhnacademy.search.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EsTagUpdater {

    private final WebClient esWebClient;
    private final ObjectMapper objectMapper;

    public EsTagUpdater(
            @Qualifier("elasticsearchWebClient")
            WebClient esWebClient,
            ObjectMapper objectMapper
    ) {
        this.esWebClient = esWebClient;
        this.objectMapper = objectMapper;
    }

    public void updateMetadataTagsByIsbn(String indexName, Map<String, List<String>> isbnToTags) {
        Map<String, Object> body = Map.of(
                "query", Map.of(
                        "terms", Map.of("metadata.isbn.keyword", isbnToTags.keySet())
                ),
                "script", Map.of(
                        "lang", "painless",
                        "source", """
                            if (ctx._source.metadata == null) return;
                            def k = ctx._source.metadata.isbn;
                            if (k == null) return;

                            k = k.toString().trim();

                            def v = params.m.get(k);
                            if (v == null) return;

                            ctx._source.metadata.tags = v;
                        """,
                        "params", Map.of("m", isbnToTags)
                )
        );

        String resp = esWebClient.post()
                .uri("/{index}/_update_by_query?conflicts=proceed&refresh=true", indexName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        logUpdateByQueryResult(indexName, isbnToTags.size(), resp);
    }

    private void logUpdateByQueryResult(String indexName, int isbnKeyCount, String resp) {
        if (resp == null || resp.isBlank()) {
            log.warn("[EsTagUpdater] _update_by_query response is empty. index={} isbnKeys={}",
                    indexName, isbnKeyCount);
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(resp);

            long took = root.path("took").asLong(-1);
            long total = root.path("total").asLong(-1);
            long updated = root.path("updated").asLong(-1);
            long noops = root.path("noops").asLong(-1);
            long versionConflicts = root.path("version_conflicts").asLong(-1);

            JsonNode failures = root.path("failures");
            int failureCount = (failures != null && failures.isArray()) ? failures.size() : 0;

            log.info("[EsTagUpdater] UBQ index={} isbnKeys={} took={}ms total={} updated={} noops={} conflicts={} failures={}",
                    indexName, isbnKeyCount, took, total, updated, noops, versionConflicts, failureCount);

            if (failureCount > 0) {
                int show = Math.min(3, failureCount);
                for (int i = 0; i < show; i++) {
                    JsonNode f = failures.get(i);
                    String causeType = f.path("cause").path("type").asText("");
                    String reason = f.path("cause").path("reason").asText("");
                    String index = f.path("index").asText(indexName);

                    log.warn("[EsTagUpdater] UBQ failure[{}] index={} type={} reason={}",
                            i, index, causeType, reason);
                }
            }
        } catch (Exception e) {
            String snippet = resp.length() > 500 ? resp.substring(0, 500) + "..." : resp;
            log.error("[EsTagUpdater] Failed to parse UBQ response. index={} isbnKeys={} respSnippet={}",
                    indexName, isbnKeyCount, snippet, e);
        }
    }
}
