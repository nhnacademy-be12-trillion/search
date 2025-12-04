package com.nhnacademy.search.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Component
public class EsBookStatsUpdater {

    private final WebClient esWebClient;
    private final ObjectMapper objectMapper;

    public EsBookStatsUpdater(@Qualifier("elasticsearchWebClient") WebClient esWebClient,
                              ObjectMapper objectMapper) {
        this.esWebClient = esWebClient;
        this.objectMapper = objectMapper;
    }

    public record BookStats(int reviewCount, int ratingCount, Double ratingAvg, double popularityScore) {}

    public void updateBookStatsByIsbn(String indexName, Map<String, BookStats> isbnToStats, int chunkSize) {
        if (isbnToStats == null || isbnToStats.isEmpty()) {
            System.out.println("[EsBookStatsUpdater] skip: empty isbnToStats");
            return;
        }

        List<String> keys = new ArrayList<>(isbnToStats.keySet());
        for (int i = 0; i < keys.size(); i += chunkSize) {
            List<String> chunk = keys.subList(i, Math.min(i + chunkSize, keys.size()));
            Map<String, BookStats> sub = new LinkedHashMap<>();
            for (String k : chunk) sub.put(k, isbnToStats.get(k));

            String resp = postUbq(indexName, sub);
            logUbq(indexName, sub.size(), resp);
        }
    }

    private String postUbq(String indexName, Map<String, BookStats> isbnToStats) {
        Map<String, Object> paramsMap = new HashMap<>();
        for (Map.Entry<String, BookStats> e : isbnToStats.entrySet()) {
            BookStats s = e.getValue();
            Map<String, Object> v = new HashMap<>();
            v.put("reviewCount", s.reviewCount());
            v.put("ratingCount", s.ratingCount());
            v.put("ratingAvg", s.ratingAvg());
            v.put("popularityScore", s.popularityScore());
            paramsMap.put(e.getKey(), v);
        }

        Map<String, Object> body = Map.of(
                "query", Map.of("terms", Map.of("metadata.isbn.keyword", isbnToStats.keySet())),
                "script", Map.of(
                        "lang", "painless",
                        "source", """
                            if (ctx._source.metadata == null) return;
                            def k = ctx._source.metadata.isbn;
                            if (k == null) return;
                            k = k.toString().trim();
                            def v = params.m.get(k);
                            if (v == null) return;

                            ctx._source.reviewCount = v.reviewCount;
                            ctx._source.ratingCount = v.ratingCount;
                            if (v.ratingAvg != null) {
                                      ctx._source.ratingAvg = v.ratingAvg;
                                    }
                            ctx._source.popularityScore = v.popularityScore;
                        """,
                        "params", Map.of("m", paramsMap)
                )
        );

        return esWebClient.post()
                .uri("/{index}/_update_by_query?conflicts=proceed&refresh=true", indexName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private void logUbq(String indexName, int keys, String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            System.out.printf(
                    "[EsBookStatsUpdater] UBQ index=%s keys=%d took=%dms total=%d updated=%d noops=%d conflicts=%d failures=%d%n",
                    indexName, keys,
                    root.path("took").asLong(-1),
                    root.path("total").asLong(-1),
                    root.path("updated").asLong(-1),
                    root.path("noops").asLong(-1),
                    root.path("version_conflicts").asLong(-1),
                    root.path("failures").isArray() ? root.path("failures").size() : 0
            );
        } catch (Exception e) {
            System.out.printf("[EsBookStatsUpdater] parse fail index=%s err=%s%n", indexName, e.getMessage());
        }
    }
}
