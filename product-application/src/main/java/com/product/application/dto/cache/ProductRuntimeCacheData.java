package com.product.application.dto.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductRuntimeCacheData(
        Long productId,
        BigDecimal salePrice,
        Integer stock,
        Boolean soldOut,
        String displayStatus,
        Instant updatedAt
) {
}
