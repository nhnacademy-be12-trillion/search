package com.nhnacademy.search.schedule;

import com.nhnacademy.search.config.PipelineEmbeddingBackfillProperties;
import com.nhnacademy.search.service.BookEmbeddingBackfillService;
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
public class EmbeddingBackfillScheduler {

    private final BookEmbeddingBackfillService service;
    private final PipelineEmbeddingBackfillProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        if (!props.isEnabled() || !props.isRunOnStartup()) return;
        runSafely("startup");
    }

    @Scheduled(cron = "${pipeline.embedding-backfill.cron:0 10 3 * * *}", zone = "Asia/Seoul")
    public void runScheduled() {
        if (!props.isEnabled()) return;
        runSafely("scheduled");
    }

    private void runSafely(String reason) {
        if (!running.compareAndSet(false, true)) {
            log.info("[EmbeddingBackfillScheduler] skip: already running. reason={}", reason);
            return;
        }
        try {
            service.fillMissingEmbeddings(props.getPageSize(), props.getMaxDocsPerRun());
        } finally {
            running.set(false);
        }
    }
}
