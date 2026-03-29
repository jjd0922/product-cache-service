package com.product.application.factory;

import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.domain.product.exception.ProductErrorCode;
import com.product.domain.product.exception.ProductException;
import com.product.domain.product.model.ProductAvailability;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class ProductRuntimeCacheFactory {

    public ProductRuntimeCacheData from(Long productId, BigDecimal salePrice, Integer stock, Instant updatedAt) {
        if (productId == null || productId < 1) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_ID);
        }

        Instant resolvedUpdatedAt = updatedAt != null ? updatedAt : Instant.now();

        if (stock == null) {
            return new ProductRuntimeCacheData(
                    productId,
                    salePrice,
                    null,
                    null,
                    null,
                    resolvedUpdatedAt
            );
        }

        ProductAvailability availability = ProductAvailability.from(stock);

        return new ProductRuntimeCacheData(
                productId,
                salePrice,
                stock,
                availability.isSoldOut(),
                availability.displayStatus().name(),
                resolvedUpdatedAt
        );
    }
}