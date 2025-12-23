package com.nhnacademy.search.schedule;

import com.nhnacademy.search.config.BookStatsSyncProperties;
import com.nhnacademy.search.service.BookStatsSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookStatsScheduler {

    private final BookStatsSyncService service;
    private final BookStatsSyncProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        if (!props.isEnabled() || !props.isRunOnStartup()) return;
        runSafely("startup");
    }

    @Scheduled(cron = "${pipeline.book-stats.cron:0 40 2 * * *}", zone = "Asia/Seoul")
    public void runNightly() {
        if (!props.isEnabled()) return;
        runSafely("scheduled");
    }

    private void runSafely(String reason) {
        if (!running.compareAndSet(false, true)) {
            log.info("[BookStatsScheduler] skip: already running. reason={}", reason);
            return;
        }
        try {
            log.info("[BookStatsScheduler] start reason={}", reason);
            service.runFullSync();
            log.info("[BookStatsScheduler] done reason={}", reason);
        } finally {
            running.set(false);
        }
    }
}
