package com.product.application.dto.command;

public record ProductCacheChangedCommand(
        Long productId,
        ProductCacheChangeType changeType
) {
}
