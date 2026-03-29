package com.product.application.dto.cache;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductRuntimeCacheData(
        Long productId,
        BigDecimal salePrice,
        Integer stock,
        Boolean soldOut,
        String displayStatus,
        Instant updatedAt
) {
}