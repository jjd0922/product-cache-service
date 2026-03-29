package com.product.application.dto.result;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResult(
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