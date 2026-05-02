package com.product.application.cache;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class RebuildJob {

    private final UUID jobId;
    private final long total;
    private final String filterSummary;
    private final LocalDateTime startedAt;

    private RebuildJobStatus status;
    private long processed;
    private String message;
    private String failureReason;
    private LocalDateTime finishedAt;

    private RebuildJob(UUID jobId, long total, String filterSummary, LocalDateTime startedAt) {
        this.jobId = jobId;
        this.total = Math.max(total, 0L);
        this.filterSummary = filterSummary;
        this.startedAt = startedAt;
        this.status = RebuildJobStatus.QUEUED;
        this.processed = 0L;
        this.message = "캐시 재빌드 작업이 대기 중입니다.";
        this.failureReason = null;
        this.finishedAt = null;
    }

    public static RebuildJob queued(String filterSummary, long total) {
        return new RebuildJob(UUID.randomUUID(), total, filterSummary, LocalDateTime.now());
    }

    public synchronized void markRunning(String message) {
        this.status = RebuildJobStatus.RUNNING;
        this.message = message;
        this.failureReason = null;
        this.finishedAt = null;
    }

    public synchronized void updateProgress(long processed, String message) {
        this.processed = Math.min(Math.max(processed, 0L), this.total);
        this.message = message;
    }

    public synchronized void markSucceeded(String message) {
        this.status = RebuildJobStatus.SUCCEEDED;
        this.processed = this.total;
        this.message = message;
        this.failureReason = null;
        this.finishedAt = LocalDateTime.now();
    }

    public synchronized void markFailed(String message, String failureReason) {
        this.status = RebuildJobStatus.FAILED;
        this.message = message;
        this.failureReason = failureReason;
        this.finishedAt = LocalDateTime.now();
    }

    public synchronized int getProgressPercent() {
        if (total <= 0) {
            return 100;
        }
        return (int) Math.min(100L, (processed * 100L) / total);
    }

    public synchronized boolean isActive() {
        return status == RebuildJobStatus.QUEUED || status == RebuildJobStatus.RUNNING;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                jobId,
                total,
                filterSummary,
                startedAt,
                status,
                processed,
                getProgressPercent(),
                isActive(),
                message,
                failureReason,
                finishedAt
        );
    }

    public record Snapshot(
            UUID jobId,
            long total,
            String filterSummary,
            LocalDateTime startedAt,
            RebuildJobStatus status,
            long processed,
            int progressPercent,
            boolean active,
            String message,
            String failureReason,
            LocalDateTime finishedAt
    ) {
    }
}
