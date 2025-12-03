package com.nhnacademy.search.schedule;

import com.nhnacademy.search.config.ReviewSummaryPipelineProperties;
import com.nhnacademy.search.service.ReviewSummaryPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class ReviewSummaryScheduler {

    private final ReviewSummaryPipelineService pipeline;
    private final ReviewSummaryPipelineProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // 앱 켜질 때 1번
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        if (!props.isEnabled() || !props.isRunOnStartup()) return;
        runSafely("startup");
    }

    // 매일 새벽 2시 30분
    @Scheduled(cron = "${pipeline.review-summary.cron:0 30 2 * * *}", zone = "Asia/Seoul")
    public void runNightly() {
        if (!props.isEnabled()) return;
        runSafely("scheduled");
    }

    private void runSafely(String reason) {
        if (!running.compareAndSet(false, true)) {
            System.out.println("[ReviewSummaryScheduler] skip: already running. reason=" + reason);
            return;
        }
        try {
            pipeline.runOnce(props.getRecentReviewLimit(), props.getPerBookReviewLimit());
        } finally {
            running.set(false);
        }
    }
}
