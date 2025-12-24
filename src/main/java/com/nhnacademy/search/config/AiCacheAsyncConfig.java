package com.nhnacademy.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AiCacheAsyncConfig {

    // 백그라운드 갱신용 스레드 풀
    @Bean(name = "aiCacheRefreshExecutor")
    public Executor aiCacheRefreshExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("ai-cache-refresh-");
        ex.initialize();
        return ex;
    }
}

