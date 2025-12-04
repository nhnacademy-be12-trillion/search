package com.nhnacademy.search.schedule;

import com.nhnacademy.search.config.BookStatsSyncProperties;
import com.nhnacademy.search.service.BookStatsSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

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
            System.out.println("[BookStatsScheduler] skip: already running. reason=" + reason);
            return;
        }
        try {
            System.out.println("[BookStatsScheduler] start reason=" + reason);
            service.runFullSync();
            System.out.println("[BookStatsScheduler] done reason=" + reason);
        } finally {
            running.set(false);
        }
    }
}
