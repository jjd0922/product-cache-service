package com.product.infrastructure.cache.support;

import com.product.infrastructure.config.ProductCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCacheKeyGenerator {

    private final ProductCacheProperties properties;

    public String detailKey(Long productId) {
        return properties.getDetailKeyPrefix() + productId;
    }

    public String runtimeKey(Long productId) {
        return properties.getRuntimeKeyPrefix() + productId;
    }

}