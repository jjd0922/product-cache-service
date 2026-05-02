package com.product.application.service;

import com.product.application.cache.RebuildJob;
import com.product.application.cache.RebuildRequest;
import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.dto.result.RebuildJobResult;
import com.product.application.port.in.ProductCacheAdminUseCase;
import com.product.application.port.out.RebuildJobStore;
import com.product.domain.product.exception.ProductErrorCode;
import com.product.domain.product.exception.ProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCacheRebuildService implements ProductCacheAdminUseCase {

    private final ProductCacheRebuildPlanner productCacheRebuildPlanner;
    private final ProductCacheRebuildAsyncWorker productCacheRebuildAsyncWorker;
    private final RebuildJobStore rebuildJobStore;

    @Override
    public RebuildJobResult rebuild(ProductCacheRebuildCommand command) {
        validateNoActiveJob();

        RebuildRequest request = productCacheRebuildPlanner.plan(command);
        RebuildJob job = rebuildJobStore.createIfAbsentActive(request.filterSummary(), request.totalCount())
                .orElseThrow(this::alreadyRunningException);

        if (request.isEmpty()) {
            rebuildJobStore.markSucceeded(job.getJobId(), "재빌드 대상 상품이 없습니다.");
            return getJob(job.getJobId());
        }

        productCacheRebuildAsyncWorker.rebuild(job.getJobId(), request);
        return getJob(job.getJobId());
    }

    @Override
    public RebuildJobResult getJob(UUID jobId) {
        RebuildJob job = rebuildJobStore.find(jobId)
                .orElseThrow(() -> new ProductException(
                        ProductErrorCode.REBUILD_JOB_NOT_FOUND,
                        "존재하지 않는 jobId 입니다. jobId=" + jobId
                ));

        RebuildJob.Snapshot snapshot = job.snapshot();

        return new RebuildJobResult(
                snapshot.jobId(),
                snapshot.status().name(),
                snapshot.total(),
                snapshot.processed(),
                snapshot.progressPercent(),
                snapshot.active(),
                snapshot.message(),
                snapshot.failureReason(),
                snapshot.filterSummary(),
                snapshot.startedAt(),
                snapshot.finishedAt()
        );
    }

    private void validateNoActiveJob() {
        rebuildJobStore.findActiveJob().ifPresent(job -> {
            throw new ProductException(
                    ProductErrorCode.REBUILD_JOB_ALREADY_RUNNING,
                    String.format(
                            "이미 진행 중인 캐시 재빌드 작업이 있습니다. activeJobId=%s, status=%s",
                            job.getJobId(),
                            job.getStatus().name()
                    )
            );
        });
    }

    private ProductException alreadyRunningException() {
        RebuildJob activeJob = rebuildJobStore.findActiveJob().orElse(null);

        if (activeJob == null) {
            return new ProductException(
                    ProductErrorCode.REBUILD_JOB_ALREADY_RUNNING,
                    "이미 진행 중인 캐시 재빌드 작업이 있습니다."
            );
        }

        return new ProductException(
                ProductErrorCode.REBUILD_JOB_ALREADY_RUNNING,
                String.format(
                        "이미 진행 중인 캐시 재빌드 작업이 있습니다. activeJobId=%s, status=%s",
                        activeJob.getJobId(),
                        activeJob.getStatus().name()
                )
        );
    }
}
