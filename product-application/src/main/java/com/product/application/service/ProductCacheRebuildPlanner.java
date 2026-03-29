package com.product.application.service;

import com.product.application.cache.RebuildRequest;
import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.port.out.ProductReadPort;
import com.product.domain.product.exception.ProductErrorCode;
import com.product.domain.product.exception.ProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductCacheRebuildPlanner {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int MAX_REBUILD_TARGET_COUNT = 30_000;

    private final ProductReadPort productReadPort;

    public RebuildRequest plan(ProductCacheRebuildCommand command) {
        List<Long> targetIds;
        String filterSummary;

        if (command == null || command.productIds() == null || command.productIds().isEmpty()) {
            targetIds = productReadPort.findAllIds();
            filterSummary = "ALL";
        } else {
            targetIds = normalizeIds(command.productIds());
            filterSummary = "IDS(" + targetIds.size() + ")";
        }

        validateTargetCount(targetIds.size(), filterSummary);

        return new RebuildRequest(targetIds, DEFAULT_CHUNK_SIZE, filterSummary);
    }

    private void validateTargetCount(int targetCount, String filterSummary) {
        if (targetCount > MAX_REBUILD_TARGET_COUNT) {
            throw new ProductException(
                    ProductErrorCode.REBUILD_REQUEST_LIMIT_EXCEEDED,
                    String.format(
                            "재빌드 요청 제한을 초과했습니다. filter=%s, requested=%d, max=%d",
                            filterSummary,
                            targetCount,
                            MAX_REBUILD_TARGET_COUNT
                    )
            );
        }
    }

    private List<Long> normalizeIds(List<Long> productIds) {
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (productId != null && productId > 0) {
                normalized.add(productId);
            }
        }
        return new ArrayList<>(normalized);
    }
}