package com.nhnacademy.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pipeline.book-stats")
public class BookStatsSyncProperties {
    private boolean enabled = false;
    private boolean runOnStartup = true;

    // Book 테이블 페이지 사이즈 - DB 조회 단위
    private int pageSize = 2000;

    // ES UBQ terms 키 개수
    private int ubqChunkSize = 500;

    // 스케줄링: 매일 02:40
    private String cron = "0 40 2 * * *";
}
