package com.nhnacademy.search.controller;

import com.nhnacademy.search.config.ElasticsearchProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/es")
public class ElasticsearchPingController {

    private final WebClient elasticsearchWebClient;
    private final ElasticsearchProperties properties;

    public ElasticsearchPingController(WebClient elasticsearchWebClient,
                                       ElasticsearchProperties properties) {
        this.elasticsearchWebClient = elasticsearchWebClient;
        this.properties = properties;
    }

    @GetMapping("/ping")
    public Mono<String> ping() {
        return elasticsearchWebClient.get()
                .uri("/")
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/sample-search")
    public String sampleSearch() {
        Map<String, Object> body = Map.of(
                "size", 1,
                "query", Map.of("match_all", Map.of())
        );

        return elasticsearchWebClient.post()
                .uri("/{index}/_search", properties.getIndex().getBook())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}

