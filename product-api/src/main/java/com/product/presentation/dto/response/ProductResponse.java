package com.product.presentation.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        BigDecimal salePrice,
        Integer stock,
        Boolean soldOut,
        String displayStatus,
        Instant updatedAt
) {
}