package com.product.application.dto.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.product.application.dto.cache.ProductRuntimeCacheData;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
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
    public ProductResult applyRuntime(ProductRuntimeCacheData runtime) {
        if (runtime == null) {
            return this;
        }

        return new ProductResult(
                id,
                name,
                price,
                runtime.salePrice() != null ? runtime.salePrice() : salePrice,
                runtime.stock() != null ? runtime.stock() : stock,
                runtime.soldOut() != null ? runtime.soldOut() : soldOut,
                runtime.displayStatus() != null ? runtime.displayStatus() : displayStatus,
                runtime.updatedAt() != null ? runtime.updatedAt() : updatedAt
        );
    }
}
