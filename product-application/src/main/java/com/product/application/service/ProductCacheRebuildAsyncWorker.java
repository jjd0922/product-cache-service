package com.product.application.service;

import com.product.application.cache.RebuildRequest;
import com.product.application.common.failure.FailureReasonBuilder;
import com.product.application.port.out.ProductCacheMetricsPort;
import com.product.application.port.out.ProductReadPort;
import com.product.application.port.out.RebuildJobStore;
import com.product.domain.product.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheRebuildAsyncWorker {

    private final ProductReadPort productReadPort;
    private final ProductCacheRefreshService productCacheRefreshService;
    private final RebuildJobStore rebuildJobStore;
    private final ProductCacheMetricsPort productCacheMetricsPort;

    @Async
    public void rebuild(UUID jobId, RebuildRequest request) {
        long totalStartNs = System.nanoTime();

        try {
            if (request == null) {
                productCacheMetricsPort.recordRebuildFailed();
                rebuildJobStore.markFailed(
                        jobId,
                        "Cache rebuild failed.",
                        "RebuildRequest must not be null"
                );
                return;
            }

            if (request.isEmpty()) {
                productCacheMetricsPort.recordRebuildCompleted(0L, 0L, elapsedMs(totalStartNs));
                rebuildJobStore.markSucceeded(jobId, "No products to rebuild.");
                return;
            }

            int chunkSize = request.chunkSize();
            if (chunkSize < 1) {
                productCacheMetricsPort.recordRebuildFailed();
                rebuildJobStore.markFailed(
                        jobId,
                        "Cache rebuild failed.",
                        "chunkSize must be greater than or equal to 1. chunkSize=" + chunkSize
                );
                return;
            }

            long totalCount = request.totalCount();
            int totalChunks = (int) ((totalCount + chunkSize - 1) / chunkSize);

            rebuildJobStore.markRunning(
                    jobId,
                    String.format(
                            "Cache rebuild started. total=%d, chunkSize=%d, chunks=%d",
                            totalCount,
                            chunkSize,
                            totalChunks
                    )
            );

            long processed = request.allProducts()
                    ? rebuildAllProducts(jobId, chunkSize, totalChunks)
                    : rebuildRequestedProducts(jobId, request.targetProductIds(), chunkSize, totalChunks);

            long totalElapsedMs = elapsedMs(totalStartNs);
            productCacheMetricsPort.recordRebuildCompleted(totalCount, processed, totalElapsedMs);

            rebuildJobStore.markSucceeded(
                    jobId,
                    String.format(
                            "Cache rebuild completed. total=%d, processed=%d, elapsedMs=%d",
                            totalCount,
                            processed,
                            totalElapsedMs
                    )
            );
        } catch (Exception e) {
            productCacheMetricsPort.recordRebuildFailed();
            String failureReason = FailureReasonBuilder.from(e);

            log.error(
                    "Cache rebuild failed. jobId={}, failureReason={}",
                    jobId,
                    failureReason,
                    e
            );

            rebuildJobStore.markFailed(
                    jobId,
                    "Cache rebuild failed.",
                    failureReason
            );
        }
    }

    private long rebuildAllProducts(UUID jobId, int chunkSize, int totalChunks) {
        long processed = 0L;
        long lastProductId = 0L;

        for (int chunkNo = 1; ; chunkNo++) {
            List<Long> chunkIds = productReadPort.findIdsAfter(lastProductId, chunkSize);
            if (chunkIds.isEmpty()) {
                return processed;
            }

            lastProductId = chunkIds.get(chunkIds.size() - 1);
            processed += processChunk(jobId, chunkIds, chunkNo, totalChunks, processed);
        }
    }

    private long rebuildRequestedProducts(UUID jobId, List<Long> targetIds, int chunkSize, int totalChunks) {
        long processed = 0L;

        for (int start = 0, chunkNo = 1; start < targetIds.size(); start += chunkSize, chunkNo++) {
            int end = Math.min(start + chunkSize, targetIds.size());
            List<Long> chunkIds = targetIds.subList(start, end);

            processed += processChunk(jobId, chunkIds, chunkNo, totalChunks, processed);
        }

        return processed;
    }

    private long processChunk(UUID jobId, List<Long> chunkIds, int chunkNo, int totalChunks, long processedBeforeChunk) {
        long chunkStartNs = System.nanoTime();

        List<Product> products = productReadPort.findAllByIdIn(chunkIds);
        if (!products.isEmpty()) {
            productCacheRefreshService.refreshAll(products);
        }

        long processed = processedBeforeChunk + chunkIds.size();
        long elapsedMs = elapsedMs(chunkStartNs);
        int loadedCount = products.size();
        int missingCount = Math.max(0, chunkIds.size() - loadedCount);

        productCacheMetricsPort.recordRebuildChunk(chunkIds.size(), loadedCount, elapsedMs);

        String progressMessage = String.format(
                "Chunk %d/%d completed. requested=%d, loaded=%d, missing=%d, elapsedMs=%d",
                chunkNo,
                totalChunks,
                chunkIds.size(),
                loadedCount,
                missingCount,
                elapsedMs
        );

        log.info(
                "Cache rebuild chunk completed. jobId={}, chunk={}/{}, requested={}, loaded={}, missing={}, elapsedMs={}",
                jobId,
                chunkNo,
                totalChunks,
                chunkIds.size(),
                loadedCount,
                missingCount,
                elapsedMs
        );

        rebuildJobStore.updateProgress(jobId, processed, progressMessage);
        return chunkIds.size();
    }

    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }
}
