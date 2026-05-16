package com.product.application.port.out;

public interface ProductCacheMetricsPort {

    void recordCacheRead(String cacheName, long requestedCount, long hitCount, boolean failed);

    void recordCacheWrite(String cacheName, long itemCount, boolean failed);

    void recordDbFallback(long requestedCount, long loadedCount);

    void recordNotFoundCacheHit(long hitCount);

    void recordRebuildChunk(long requestedCount, long loadedCount, long elapsedMs);

    void recordRebuildCompleted(long totalCount, long processedCount, long elapsedMs);

    void recordRebuildFailed();

    void recordCacheEventHandled(String changeType, boolean failed);
}
