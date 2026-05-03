package com.product.infrastructure.event;

import com.product.application.dto.command.ProductCacheChangeType;

public record ProductChangedEvent(
        Long productId,
        ProductCacheChangeType changeType
) {
}
