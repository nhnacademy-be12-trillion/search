package com.nhnacademy.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String baseUrl,
        String apiKey,
        String model,
        Duration timeout
) {
}
