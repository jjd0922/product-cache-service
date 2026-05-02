package com.product.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.application.cache.RebuildJob;
import com.product.application.port.out.RebuildJobStore;
import com.product.infrastructure.config.ProductCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "product.cache", name = "rebuild-job-store", havingValue = "redis")
public class RedisRebuildJobStore implements RebuildJobStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductCacheProperties properties;

    @Override
    public Optional<RebuildJob> createIfAbsentActive(String filterSummary, long totalCount) {
        clearStaleActiveJob();

        RebuildJob job = RebuildJob.queued(filterSummary, totalCount);
        Boolean acquired = valueOperations().setIfAbsent(
                properties.getRebuildActiveKey(),
                job.getJobId().toString(),
                Duration.ofSeconds(properties.getRebuildActiveTtlSeconds())
        );

        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }

        save(job);
        return Optional.of(job);
    }

    @Override
    public Optional<RebuildJob> find(UUID jobId) {
        if (jobId == null) {
            return Optional.empty();
        }

        String payload = valueOperations().get(jobKey(jobId));
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            RebuildJob.Snapshot snapshot = objectMapper.readValue(payload, RebuildJob.Snapshot.class);
            return Optional.of(RebuildJob.restore(snapshot));
        } catch (JsonProcessingException e) {
            log.warn("event=rebuild_job_deserialize_error jobId={} message={}", jobId, e.getMessage());
            redisTemplate.delete(jobKey(jobId));
            return Optional.empty();
        }
    }

    @Override
    public Optional<RebuildJob> findActiveJob() {
        UUID activeJobId = activeJobId();
        if (activeJobId == null) {
            return Optional.empty();
        }

        Optional<RebuildJob> activeJob = find(activeJobId);
        if (activeJob.isEmpty() || !activeJob.get().isActive()) {
            deleteActiveKey(activeJobId);
            return Optional.empty();
        }

        return activeJob;
    }

    @Override
    public void markRunning(UUID jobId, String message) {
        find(jobId).ifPresent(job -> {
            job.markRunning(message);
            save(job);
        });
    }

    @Override
    public void updateProgress(UUID jobId, long processed, String message) {
        find(jobId).ifPresent(job -> {
            job.updateProgress(processed, message);
            save(job);
        });
    }

    @Override
    public void markSucceeded(UUID jobId, String message) {
        find(jobId).ifPresent(job -> {
            job.markSucceeded(message);
            save(job);
            deleteActiveKey(jobId);
        });
    }

    @Override
    public void markFailed(UUID jobId, String message, String failureReason) {
        find(jobId).ifPresent(job -> {
            job.markFailed(message, failureReason);
            save(job);
            deleteActiveKey(jobId);
        });
    }

    private void save(RebuildJob job) {
        try {
            valueOperations().set(
                    jobKey(job.getJobId()),
                    objectMapper.writeValueAsString(job.snapshot()),
                    Duration.ofSeconds(properties.getRebuildJobTtlSeconds())
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rebuild job. jobId=" + job.getJobId(), e);
        }
    }

    private void clearStaleActiveJob() {
        findActiveJob();
    }

    private UUID activeJobId() {
        String activeJobId = valueOperations().get(properties.getRebuildActiveKey());
        if (activeJobId == null || activeJobId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(activeJobId);
        } catch (IllegalArgumentException e) {
            redisTemplate.delete(properties.getRebuildActiveKey());
            return null;
        }
    }

    private void deleteActiveKey(UUID jobId) {
        UUID activeJobId = activeJobId();
        if (jobId != null && jobId.equals(activeJobId)) {
            redisTemplate.delete(properties.getRebuildActiveKey());
        }
    }

    private String jobKey(UUID jobId) {
        return properties.getRebuildJobKeyPrefix() + jobId;
    }

    private ValueOperations<String, String> valueOperations() {
        return redisTemplate.opsForValue();
    }
}
