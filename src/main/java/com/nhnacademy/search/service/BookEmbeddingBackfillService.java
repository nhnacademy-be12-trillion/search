package com.nhnacademy.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.search.config.AiSearchProperties;
import com.nhnacademy.search.config.ElasticsearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
public class BookEmbeddingBackfillService {

    private final WebClient esWebClient;
    private final WebClient ollamaWebClient;
    private final ElasticsearchProperties esProps;
    private final AiSearchProperties aiProps;
    private final ObjectMapper objectMapper;

    public BookEmbeddingBackfillService(
            @Qualifier("elasticsearchWebClient") WebClient esWebClient,
            @Qualifier("ollamaWebClient") WebClient ollamaWebClient,
            ElasticsearchProperties esProps,
            AiSearchProperties aiProps,
            ObjectMapper objectMapper
    ) {
        this.esWebClient = esWebClient;
        this.ollamaWebClient = ollamaWebClient;
        this.esProps = esProps;
        this.aiProps = aiProps;
        this.objectMapper = objectMapper;
    }

    /*
     * embeddingVector가 없는 문서만 찾아서 채움
     * @param pageSize 한 번에 처리할 ES hits 수
     * @param maxDocs  최대 처리 문서 수 ( 0 == 제한 없음 )
     * @return 업데이트한 문서 수
     */
    public int fillMissingEmbeddings(int pageSize, int maxDocs) {
        String indexName = esProps.getIndex().getBook();
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalStateException("elasticsearch.index.book is blank");
        }

        String field = aiProps.getSearch().getEmbeddingField();
        if (field == null || field.isBlank()) field = "embeddingVector";

        int updated = 0;
        Object searchAfter = null;

        while (true) {
            if (maxDocs > 0 && updated >= maxDocs) break;

            Map<String, Object> q = buildMissingEmbeddingQuery(field, pageSize, searchAfter);

            Map<String, Object> resp = esWebClient.post()
                    .uri("/" + indexName + "/_search")
                    .bodyValue(q)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            List<Hit> hits = parseHits(resp);
            if (hits.isEmpty()) break;

            // bulk update payload
            StringBuilder ndjson = new StringBuilder();

            for (Hit h : hits) {
                if (maxDocs > 0 && updated >= maxDocs) break;

                String text = buildEmbeddingText(h.source);
                if (text.isBlank()) continue;

                List<Float> vec = embed(text);

                // action line
                ndjson.append("{\"update\":{\"_index\":\"")
                        .append(indexName)
                        .append("\",\"_id\":\"")
                        .append(h.id)
                        .append("\"}}\n");

                // doc line
                Map<String, Object> doc = Map.of("doc", Map.of(field, vec));
                try {
                    ndjson.append(objectMapper.writeValueAsString(doc)).append("\n");
                } catch (Exception e) {
                    log.error("[EmbeddingBackfill] json serialize failed. id={}", h.id, e);
                    continue;
                }

                updated++;
            }

            if (ndjson.length() == 0) {
                // 이번 페이지에서 전부 스킵된 경우 -> 다음 페이지로
                searchAfter = hits.get(hits.size() - 1).sortValue;
                continue;
            }

            String bulkResp = esWebClient.post()
                    .uri("/_bulk")
                    .contentType(MediaType.APPLICATION_NDJSON)
                    .bodyValue(ndjson.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("[EmbeddingBackfill] bulk updated batch. hits={}, updatedSoFar={}, resp={}",
                    hits.size(), updated, bulkResp);

            // search_after 업데이트
            searchAfter = hits.get(hits.size() - 1).sortValue;
        }

        log.info("[EmbeddingBackfill] done. updated={}", updated);
        return updated;
    }

    private Map<String, Object> buildMissingEmbeddingQuery(String field, int size, Object searchAfter) {
        Map<String, Object> query = Map.of(
                "bool", Map.of(
                        "must", List.of(Map.of("match_all", Map.of())),
                        "must_not", List.of(Map.of("exists", Map.of("field", field)))
                )
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", size);
        body.put("query", query);

        // search_after를 쓰려면 sort가 필요
        // bookId가 ES _id로 들어가 있으니 _shard_doc 정렬로 처리
        body.put("sort", List.of(Map.of("_shard_doc", "asc")));

        // 필요한 필드만 가져오기: title/author/publisher/content
        body.put("_source", List.of(
                "metadata.title",
                "metadata.author",
                "metadata.publisher",
                "metadata.content"
        ));

        if (searchAfter != null) {
            body.put("search_after", List.of(searchAfter));
        }
        return body;
    }

    private static class Hit {
        final String id;
        final Object sortValue;
        final Map<String, Object> source;

        Hit(String id, Object sortValue, Map<String, Object> source) {
            this.id = id;
            this.sortValue = sortValue;
            this.source = source;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Hit> parseHits(Map<String, Object> esResponse) {
        if (esResponse == null) return List.of();

        Map<String, Object> hitsRoot = (Map<String, Object>) esResponse.get("hits");
        if (hitsRoot == null) return List.of();

        List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsRoot.get("hits");
        if (hits == null || hits.isEmpty()) return List.of();

        List<Hit> out = new ArrayList<>(hits.size());
        for (Map<String, Object> h : hits) {
            String id = (h.get("_id") == null) ? null : String.valueOf(h.get("_id"));
            Map<String, Object> src = (Map<String, Object>) h.get("_source");

            List<Object> sortArr = (List<Object>) h.get("sort");
            Object sortVal = (sortArr == null || sortArr.isEmpty()) ? null : sortArr.get(0);

            if (id == null || src == null || sortVal == null) continue;
            out.add(new Hit(id, sortVal, src));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private String buildEmbeddingText(Map<String, Object> src) {
        Map<String, Object> md = (src == null) ? null : (Map<String, Object>) src.get("metadata");
        if (md == null) md = Map.of();

        String title = nvl(md.get("title"));
        String author = nvl(md.get("author"));
        String publisher = nvl(md.get("publisher"));
        String content = truncate(nvl(md.get("content")), 600); // 너무 길면 비용/속도에 영향

        // 전부 비면 임베딩 의미 없음
        if (title.isBlank() && author.isBlank() && publisher.isBlank() && content.isBlank()) return "";

        return """
               제목: %s
               저자: %s
               출판사: %s
               설명: %s
               """.formatted(title, author, publisher, content).trim();
    }

    // Ollama embeddings
    @SuppressWarnings("unchecked")
    private List<Float> embed(String prompt) {
        Map<String, Object> body = Map.of(
                "model", aiProps.getOllama().getEmbedModel(),
                "prompt", prompt
        );

        Map<String, Object> resp = ollamaWebClient.post()
                .uri("/api/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (resp == null || resp.get("embedding") == null) {
            throw new IllegalStateException("Ollama embedding response invalid");
        }

        List<?> raw = (List<?>) resp.get("embedding");
        List<Float> vec = new ArrayList<>(raw.size());
        for (Object v : raw) {
            if (v instanceof Number n) vec.add(n.floatValue());
            else vec.add(Float.parseFloat(String.valueOf(v)));
        }
        return vec;
    }

    private String nvl(Object o) {
        return (o == null) ? "" : String.valueOf(o).trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }
}
