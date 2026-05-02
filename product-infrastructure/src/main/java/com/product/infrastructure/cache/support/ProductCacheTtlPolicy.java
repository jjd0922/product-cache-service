package com.product.infrastructure.cache.support;

import com.product.infrastructure.config.ProductCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class ProductCacheTtlPolicy {

    private final ProductCacheProperties properties;

    public long detailTtlSeconds() {
        return applyJitter(properties.getDetailTtlSeconds(), properties.getDetailTtlJitterSeconds());
    }

    public long runtimeTtlSeconds() {
        return applyJitter(properties.getRuntimeTtlSeconds(), properties.getRuntimeTtlJitterSeconds());
    }

    private long applyJitter(long baseTtlSeconds, long jitterSeconds) {
        if (baseTtlSeconds < 1) {
            return 1L;
        }

        if (jitterSeconds < 1) {
            return baseTtlSeconds;
        }

        long boundedJitter = Math.min(jitterSeconds, Long.MAX_VALUE - baseTtlSeconds);
        return baseTtlSeconds + ThreadLocalRandom.current().nextLong(boundedJitter + 1);
    }
}
