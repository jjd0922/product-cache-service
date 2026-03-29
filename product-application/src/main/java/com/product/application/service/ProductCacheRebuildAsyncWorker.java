package com.product.application.service;

import com.product.application.cache.RebuildRequest;
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

    @Async
    public void rebuild(UUID jobId, RebuildRequest request) {
        long totalStartNs = System.nanoTime();

        try {
            if (request == null) {
                rebuildJobStore.markFailed(
                        jobId,
                        "캐시 재빌드가 실패했습니다.",
                        "RebuildRequest 가 null 입니다."
                );
                return;
            }

            if (request.isEmpty()) {
                rebuildJobStore.markSucceeded(jobId, "재빌드 대상 상품이 없습니다.");
                return;
            }

            int chunkSize = request.chunkSize();
            if (chunkSize < 1) {
                rebuildJobStore.markFailed(
                        jobId,
                        "캐시 재빌드가 실패했습니다.",
                        "chunkSize 는 1 이상이어야 합니다. chunkSize=" + chunkSize
                );
                return;
            }

            List<Long> targetIds = request.targetProductIds();
            int totalChunks = (targetIds.size() + chunkSize - 1) / chunkSize;

            rebuildJobStore.markRunning(
                    jobId,
                    String.format(
                            "캐시 재빌드를 시작했습니다. total=%d, chunkSize=%d, chunks=%d",
                            targetIds.size(),
                            chunkSize,
                            totalChunks
                    )
            );

            long processed = 0L;

            for (int start = 0, chunkNo = 1; start < targetIds.size(); start += chunkSize, chunkNo++) {
                long chunkStartNs = System.nanoTime();

                int end = Math.min(start + chunkSize, targetIds.size());
                List<Long> chunkIds = targetIds.subList(start, end);

                List<Product> products = productReadPort.findAllByIdIn(chunkIds);
                if (!products.isEmpty()) {
                    productCacheRefreshService.refreshAll(products);
                }

                processed += chunkIds.size();

                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - chunkStartNs);
                int loadedCount = products.size();
                int missingCount = Math.max(0, chunkIds.size() - loadedCount);

                String progressMessage = String.format(
                        "청크 %d/%d 처리 완료. requested=%d, loaded=%d, missing=%d, elapsedMs=%d",
                        chunkNo,
                        totalChunks,
                        chunkIds.size(),
                        loadedCount,
                        missingCount,
                        elapsedMs
                );

                log.info(
                        "캐시 재빌드 청크 처리 완료. jobId={}, chunk={}/{}, requested={}, loaded={}, missing={}, elapsedMs={}",
                        jobId,
                        chunkNo,
                        totalChunks,
                        chunkIds.size(),
                        loadedCount,
                        missingCount,
                        elapsedMs
                );

                rebuildJobStore.updateProgress(jobId, processed, progressMessage);
            }

            long totalElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStartNs);

            rebuildJobStore.markSucceeded(
                    jobId,
                    String.format(
                            "캐시 재빌드가 완료되었습니다. total=%d, processed=%d, elapsedMs=%d",
                            targetIds.size(),
                            processed,
                            totalElapsedMs
                    )
            );
        } catch (Exception e) {
            String failureReason = buildFailureReason(e);

            log.error(
                    "캐시 재빌드 실패. jobId={}, failureReason={}",
                    jobId,
                    failureReason,
                    e
            );

            rebuildJobStore.markFailed(
                    jobId,
                    "캐시 재빌드가 실패했습니다.",
                    failureReason
            );
        }
    }

    private String buildFailureReason(Exception e) {
        if (e == null) {
            return "Unknown error";
        }

        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }

        return e.getClass().getSimpleName() + ": " + message;
    }
}