package com.nhnacademy.search.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.search.config.GeminiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GeminiClient {

    @Qualifier("geminiWebClient")
    private final WebClient geminiWebClient;

    private final GeminiProperties props;
    private final ObjectMapper objectMapper;

    // 가장 단순한 텍스트 생성/요약 호출.
    // 프롬프트에 시스템 지시문까지 같이 넣는 방식

    public String generateText(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        String raw = geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .build(props.model()))
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .map(msg -> new GeminiApiException("Gemini error: HTTP " + resp.statusCode() + " body=" + msg))
                )
                // 429/5xx 같은 경우만 가볍게 재시도
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(300))
                        .filter(ex -> ex.getMessage() != null && (
                                ex.getMessage().contains("HTTP 429") ||
                                        ex.getMessage().contains("HTTP 500") ||
                                        ex.getMessage().contains("HTTP 503")
                        )))
                .block();

        return extractText(raw);
    }

    private String extractText(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                throw new GeminiApiException("Gemini response missing text. raw=" + rawJson);
            }
            return textNode.asText();
        } catch (Exception e) {
            throw new GeminiApiException("Failed to parse Gemini response", e);
        }
    }
}
