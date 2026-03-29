package com.product.application.port.out;

import com.product.application.cache.RebuildJob;

import java.util.Optional;
import java.util.UUID;

public interface RebuildJobStore {

    Optional<RebuildJob> createIfAbsentActive(String filterSummary, long totalCount);

    Optional<RebuildJob> find(UUID jobId);

    Optional<RebuildJob> findActiveJob();

    void markRunning(UUID jobId, String message);

    void updateProgress(UUID jobId, long processed, String message);

    void markSucceeded(UUID jobId, String message);

    void markFailed(UUID jobId, String message, String failureReason);
}