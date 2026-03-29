package com.product.infrastructure.cache;

import com.product.application.cache.RebuildJob;
import com.product.application.port.out.RebuildJobStore;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class InMemoryRebuildJobStore implements RebuildJobStore {

    private final Map<UUID, RebuildJob> jobs = new ConcurrentHashMap<>();
    private final ReentrantLock createLock = new ReentrantLock();

    @Override
    public Optional<RebuildJob> createIfAbsentActive(String filterSummary, long totalCount) {
        createLock.lock();
        try {
            if (findActiveJob().isPresent()) {
                return Optional.empty();
            }

            RebuildJob job = RebuildJob.queued(filterSummary, totalCount);
            jobs.put(job.getJobId(), job);
            return Optional.of(job);
        } finally {
            createLock.unlock();
        }
    }

    @Override
    public Optional<RebuildJob> find(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Optional<RebuildJob> findActiveJob() {
        return jobs.values().stream()
                .filter(RebuildJob::isActive)
                .max(Comparator.comparing(RebuildJob::getStartedAt));
    }

    @Override
    public void markRunning(UUID jobId, String message) {
        find(jobId).ifPresent(job -> job.markRunning(message));
    }

    @Override
    public void updateProgress(UUID jobId, long processed, String message) {
        find(jobId).ifPresent(job -> job.updateProgress(processed, message));
    }

    @Override
    public void markSucceeded(UUID jobId, String message) {
        find(jobId).ifPresent(job -> job.markSucceeded(message));
    }

    @Override
    public void markFailed(UUID jobId, String message, String failureReason) {
        find(jobId).ifPresent(job -> job.markFailed(message, failureReason));
    }
}