package com.product.presentation.assembler;

import com.product.application.dto.result.RebuildJobResult;
import com.product.presentation.dto.response.CacheJobStatusResponse;
import com.product.presentation.dto.response.RebuildStartedResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductCacheAdminResponseAssembler {

    public RebuildStartedResponse toStartedResponse(RebuildJobResult result) {
        if (result == null) {
            return null;
        }

        return new RebuildStartedResponse(
                result.jobId(),
                result.status(),
                result.total(),
                result.processed(),
                result.progressPercent(),
                result.active(),
                result.message(),
                result.filterSummary(),
                result.startedAt()
        );
    }

    public CacheJobStatusResponse toStatusResponse(RebuildJobResult result) {
        if (result == null) {
            return null;
        }

        return new CacheJobStatusResponse(
                result.jobId(),
                result.status(),
                result.total(),
                result.processed(),
                result.progressPercent(),
                result.active(),
                result.message(),
                result.failureReason(),
                result.filterSummary(),
                result.startedAt(),
                result.finishedAt()
        );
    }
}