package com.product.infrastructure.cache.support;

import com.product.infrastructure.config.ProductCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCacheKeyGenerator {

    private final ProductCacheProperties properties;

    public String detailKey(Long productId) {
        return versionedPrefix("detail") + productId;
    }

    public String runtimeKey(Long productId) {
        return versionedPrefix("runtime") + productId;
    }

    public String notFoundKey(Long productId) {
        return versionedPrefix("notfound") + productId;
    }

    private String versionedPrefix(String cacheName) {
        return sanitize(properties.getKeyPrefix(), "product")
                + ":"
                + sanitize(properties.getKeyVersion(), "v1")
                + ":"
                + cacheName
                + ":";
    }

    private String sanitize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.strip().replaceAll("^:+|:+$", "");
    }
}
