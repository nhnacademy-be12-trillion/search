package com.nhnacademy.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class ElasticsearchConfig {

    private final ElasticsearchProperties properties;

    public ElasticsearchConfig(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    @Bean("elasticsearchWebClient")
    @Primary
    public WebClient elasticsearchWebClient() {
        String baseUrl = properties.getScheme() + "://" +
                properties.getHost() + ":" + properties.getPort();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers ->
                        headers.setBasicAuth(properties.getUsername(), properties.getPassword())
                )
                .build();
    }
}


