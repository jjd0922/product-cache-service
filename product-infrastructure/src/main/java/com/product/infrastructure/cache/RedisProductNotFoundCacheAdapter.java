package com.product.infrastructure.cache;

import com.product.application.common.exception.CacheOperationException;
import com.product.application.port.out.ProductNotFoundCachePort;
import com.product.infrastructure.cache.support.ProductCacheCircuitBreaker;
import com.product.infrastructure.cache.support.ProductCacheKeyGenerator;
import com.product.infrastructure.config.ProductCacheProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisProductNotFoundCacheAdapter implements ProductNotFoundCachePort {

    private static final String CACHE_NAME = "product-not-found-cache";
    private static final String MARKER_VALUE = "1";

    private final StringRedisTemplate redisTemplate;
    private final ProductCacheProperties properties;
    private final ProductCacheKeyGenerator keyGenerator;
    private final ProductCacheCircuitBreaker circuitBreaker;

    @Override
    public Set<Long> getAll(Collection<Long> productIds) {
        List<Long> validIds = normalizeIds(productIds);
        if (validIds.isEmpty()) {
            return Set.of();
        }

        List<String> keys = validIds.stream()
                .map(keyGenerator::notFoundKey)
                .toList();

        try {
            List<String> cachedValues = circuitBreaker.executeSupplier(() -> valueOperations().multiGet(keys));
            if (cachedValues == null || cachedValues.isEmpty()) {
                return Set.of();
            }

            Set<Long> hits = new LinkedHashSet<>();
            for (int i = 0; i < cachedValues.size(); i++) {
                if (cachedValues.get(i) != null) {
                    hits.add(validIds.get(i));
                }
            }
            return hits;
        } catch (DataAccessException | CallNotPermittedException e) {
            throw new CacheOperationException(CACHE_NAME, "getAll", validIds.size(), "not-found cache 조회 실패", e);
        }
    }

    @Override
    public void put(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }

        try {
            circuitBreaker.executeRunnable(() ->
                    valueOperations().set(
                            keyGenerator.notFoundKey(productId),
                            MARKER_VALUE,
                            Duration.ofSeconds(Math.max(properties.getNotFoundTtlSeconds(), 1L))
                    )
            );
        } catch (DataAccessException | CallNotPermittedException e) {
            throw new CacheOperationException(CACHE_NAME, "put", 1, "not-found cache 저장 실패", e);
        }
    }

    @Override
    public void evict(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }

        try {
            circuitBreaker.executeRunnable(() -> redisTemplate.delete(keyGenerator.notFoundKey(productId)));
        } catch (DataAccessException | CallNotPermittedException e) {
            throw new CacheOperationException(CACHE_NAME, "evict", 1, "not-found cache 삭제 실패", e);
        }
    }

    @Override
    public void evictAll(Collection<Long> productIds) {
        List<String> keys = normalizeIds(productIds).stream()
                .map(keyGenerator::notFoundKey)
                .toList();

        if (keys.isEmpty()) {
            return;
        }

        try {
            circuitBreaker.executeRunnable(() -> redisTemplate.delete(keys));
        } catch (DataAccessException | CallNotPermittedException e) {
            throw new CacheOperationException(CACHE_NAME, "evictAll", keys.size(), "not-found cache 일괄 삭제 실패", e);
        }
    }

    private ValueOperations<String, String> valueOperations() {
        return redisTemplate.opsForValue();
    }

    private List<Long> normalizeIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        Set<Long> normalized = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (isValidProductId(productId)) {
                normalized.add(productId);
            }
        }
        return List.copyOf(normalized);
    }

    private boolean isValidProductId(Long productId) {
        return productId != null && productId > 0;
    }
}
