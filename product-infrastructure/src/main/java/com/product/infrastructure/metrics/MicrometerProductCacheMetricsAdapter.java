package com.product.infrastructure.metrics;

import com.product.application.port.out.ProductCacheMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class MicrometerProductCacheMetricsAdapter implements ProductCacheMetricsPort {

    private static final String HIT = "hit";
    private static final String MISS = "miss";
    private static final String ERROR = "error";
    private static final String SUCCESS = "success";

    private final MeterRegistry meterRegistry;

    @Override
    public void recordCacheRead(String cacheName, long requestedCount, long hitCount, boolean failed) {
        if (requestedCount < 1) {
            return;
        }

        if (failed) {
            increment("product.cache.read.items", requestedCount, Tags.of("cache", cacheName, "result", ERROR));
            return;
        }

        long safeHitCount = clamp(hitCount, 0L, requestedCount);
        long missCount = requestedCount - safeHitCount;

        increment("product.cache.read.items", safeHitCount, Tags.of("cache", cacheName, "result", HIT));
        increment("product.cache.read.items", missCount, Tags.of("cache", cacheName, "result", MISS));
    }

    @Override
    public void recordCacheWrite(String cacheName, long itemCount, boolean failed) {
        if (itemCount < 1) {
            return;
        }

        String result = failed ? ERROR : SUCCESS;
        increment("product.cache.write.items", itemCount, Tags.of("cache", cacheName, "result", result));
    }

    @Override
    public void recordDbFallback(long requestedCount, long loadedCount) {
        if (requestedCount < 1) {
            return;
        }

        long safeLoadedCount = clamp(loadedCount, 0L, requestedCount);
        increment("product.cache.fallback.items", requestedCount, Tags.of("result", "requested"));
        increment("product.cache.fallback.items", safeLoadedCount, Tags.of("result", "loaded"));
    }

    @Override
    public void recordRebuildChunk(long requestedCount, long loadedCount, long elapsedMs) {
        if (requestedCount < 1) {
            return;
        }

        increment("product.cache.rebuild.chunk.items", requestedCount, Tags.of("result", "requested"));
        increment("product.cache.rebuild.chunk.items", Math.max(loadedCount, 0L), Tags.of("result", "loaded"));
        recordTime("product.cache.rebuild.chunk.duration", elapsedMs);
    }

    @Override
    public void recordRebuildCompleted(long totalCount, long processedCount, long elapsedMs) {
        increment("product.cache.rebuild.jobs", 1L, Tags.of("result", SUCCESS));
        increment("product.cache.rebuild.items", Math.max(totalCount, 0L), Tags.of("result", "total"));
        increment("product.cache.rebuild.items", Math.max(processedCount, 0L), Tags.of("result", "processed"));
        recordTime("product.cache.rebuild.duration", elapsedMs);
    }

    @Override
    public void recordRebuildFailed() {
        increment("product.cache.rebuild.jobs", 1L, Tags.of("result", ERROR));
    }

    private void increment(String metricName, long amount, Tags tags) {
        if (amount < 1) {
            return;
        }

        Counter.builder(metricName)
                .tags(tags)
                .register(meterRegistry)
                .increment(amount);
    }

    private void recordTime(String metricName, long elapsedMs) {
        Timer.builder(metricName)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(elapsedMs, 0L)));
    }

    private long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }
}
