package com.product.application.dto.result;

import com.product.application.dto.command.ProductCacheChangeType;

import java.time.Instant;

public record ProductCacheEventDlqResult(
        String eventId,
        Long productId,
        ProductCacheChangeType changeType,
        String failureReason,
        Instant createdAt
) {
}
