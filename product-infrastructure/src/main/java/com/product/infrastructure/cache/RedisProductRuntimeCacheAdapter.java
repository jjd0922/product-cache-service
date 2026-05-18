package com.product.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.application.common.exception.CacheOperationException;
import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.application.port.out.ProductRuntimeCachePort;
import com.product.infrastructure.cache.support.ProductCacheCircuitBreaker;
import com.product.infrastructure.cache.support.ProductCacheKeyGenerator;
import com.product.infrastructure.cache.support.RedisCacheBatchExecutor;
import com.product.infrastructure.cache.support.ProductCacheTtlPolicy;
import com.product.infrastructure.config.ProductCacheProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisProductRuntimeCacheAdapter implements ProductRuntimeCachePort {

    private static final String CACHE_NAME = "product-runtime-cache";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductCacheProperties properties;
    private final ProductCacheKeyGenerator keyGenerator;
    private final RedisCacheBatchExecutor batchExecutor;
    private final ProductCacheTtlPolicy ttlPolicy;
    private final ProductCacheCircuitBreaker circuitBreaker;

    @Override
    public Optional<ProductRuntimeCacheData> get(Long productId) {
        if (!isValidProductId(productId)) {
            return Optional.empty();
        }

        String key = keyGenerator.runtimeKey(productId);

        try {
            String cached = circuitBreaker.executeSupplier(() -> valueOperations().get(key));
            if (cached == null) {
                log.debug("event=cache_get_miss cache={} key={} productId={}", CACHE_NAME, key, productId);
                return Optional.empty();
            }

            ProductRuntimeCacheData runtimeData = objectMapper.readValue(cached, ProductRuntimeCacheData.class);
            if (!isExpectedPayload(productId, runtimeData)) {
                log.warn(
                        "event=cache_get_id_mismatch cache={} key={} requestedProductId={} payloadProductId={}",
                        CACHE_NAME,
                        key,
                        productId,
                        runtimeData != null ? runtimeData.productId() : null
                );
                deleteQuietly(key, productId);
                return Optional.empty();
            }

            log.debug("event=cache_get_hit cache={} key={} productId={}", CACHE_NAME, key, productId);
            return Optional.of(runtimeData);
        } catch (JsonProcessingException e) {
            log.warn(
                    "event=cache_get_deserialize_error cache={} key={} productId={} message={}",
                    CACHE_NAME, key, productId, e.getMessage()
            );
            deleteQuietly(key, productId);
            return Optional.empty();
        } catch (DataAccessException | CallNotPermittedException e) {
            log.warn(
                    "event=cache_get_error cache={} key={} productId={} message={}",
                    CACHE_NAME, key, productId, e.getMessage()
            );
            return Optional.empty();
        }
    }

    @Override
    public Map<Long, ProductRuntimeCacheData> getAll(Collection<Long> productIds) {
        List<Long> validIds = normalizeIds(productIds);
        if (validIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = validIds.stream()
                .map(keyGenerator::runtimeKey)
                .toList();

        try {
            List<String> cachedValues = circuitBreaker.executeSupplier(() -> valueOperations().multiGet(keys));
            if (cachedValues == null || cachedValues.isEmpty()) {
                return Map.of();
            }

            Map<Long, ProductRuntimeCacheData> results = new LinkedHashMap<>();

            for (int i = 0; i < keys.size(); i++) {
                String cached = cachedValues.get(i);
                if (cached == null) {
                    continue;
                }

                Long productId = validIds.get(i);
                String key = keys.get(i);

                try {
                    ProductRuntimeCacheData runtimeData = objectMapper.readValue(cached, ProductRuntimeCacheData.class);
                    if (!isExpectedPayload(productId, runtimeData)) {
                        log.warn(
                                "event=cache_batch_get_id_mismatch cache={} key={} requestedProductId={} payloadProductId={}",
                                CACHE_NAME,
                                key,
                                productId,
                                runtimeData != null ? runtimeData.productId() : null
                        );
                        deleteQuietly(key, productId);
                        continue;
                    }

                    results.put(productId, runtimeData);
                } catch (JsonProcessingException e) {
                    log.warn(
                            "event=cache_batch_get_deserialize_error cache={} key={} productId={} message={}",
                            CACHE_NAME, key, productId, e.getMessage()
                    );
                    deleteQuietly(key, productId);
                }
            }

            return results;
        } catch (DataAccessException | CallNotPermittedException e) {
            log.warn(
                    "event=cache_batch_get_error cache={} keyCount={} message={}",
                    CACHE_NAME, keys.size(), e.getMessage()
            );
            return Map.of();
        }
    }

    @Override
    public void put(ProductRuntimeCacheData runtimeCacheData) {
        if (runtimeCacheData == null || !isValidProductId(runtimeCacheData.productId())) {
            return;
        }

        String key = keyGenerator.runtimeKey(runtimeCacheData.productId());

        try {
            String payload = objectMapper.writeValueAsString(runtimeCacheData);
            circuitBreaker.executeRunnable(() ->
                    valueOperations().set(key, payload, Duration.ofSeconds(ttlPolicy.runtimeTtlSeconds()))
            );
            log.debug(
                    "event=cache_put_success cache={} key={} productId={}",
                    CACHE_NAME, key, runtimeCacheData.productId()
            );
        } catch (JsonProcessingException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "put",
                    1,
                    "상품 runtime cache 직렬화 실패",
                    e
            );
        } catch (DataAccessException | CallNotPermittedException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "put",
                    1,
                    "상품 runtime cache 저장 실패",
                    e
            );
        }
    }

    @Override
    public void putAll(Collection<ProductRuntimeCacheData> runtimeCacheDataList) {
        if (runtimeCacheDataList == null || runtimeCacheDataList.isEmpty()) {
            return;
        }

        List<ProductRuntimeCacheData> validData = runtimeCacheDataList.stream()
                .filter(Objects::nonNull)
                .filter(data -> isValidProductId(data.productId()))
                .toList();

        if (validData.isEmpty()) {
            return;
        }

        int batchSize = properties.getPipelineBatchSize();
        if (batchSize < 1) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "putAll",
                    validData.size(),
                    "pipelineBatchSize 는 1 이상이어야 합니다"
            );
        }

        List<RedisCacheBatchExecutor.CacheEntry> entries = new ArrayList<>(validData.size());
        try {
            for (ProductRuntimeCacheData data : validData) {
                entries.add(new RedisCacheBatchExecutor.CacheEntry(
                        keyGenerator.runtimeKey(data.productId()),
                        objectMapper.writeValueAsString(data)
                ));
            }
        } catch (JsonProcessingException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "putAll",
                    validData.size(),
                    "상품 runtime cache 배치 직렬화 실패",
                    e
            );
        }

        for (int start = 0; start < entries.size(); start += batchSize) {
            List<RedisCacheBatchExecutor.CacheEntry> batch = entries.subList(
                    start,
                    Math.min(start + batchSize, entries.size())
            );

            try {
                circuitBreaker.executeRunnable(() -> batchExecutor.setExBatch(batch, ttlPolicy.runtimeTtlSeconds()));
            } catch (RuntimeException e) {
                throw new CacheOperationException(
                        CACHE_NAME,
                        "putAll",
                        batch.size(),
                        "상품 runtime cache pipeline 저장 실패",
                        e
                );
            }
        }

        log.debug("event=cache_batch_put_success cache={} itemCount={}", CACHE_NAME, entries.size());
    }

    @Override
    public void evict(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }

        String key = keyGenerator.runtimeKey(productId);

        try {
            circuitBreaker.executeRunnable(() -> redisTemplate.delete(key));
            log.debug("event=cache_evict_success cache={} key={} productId={}", CACHE_NAME, key, productId);
        } catch (DataAccessException | CallNotPermittedException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "evict",
                    1,
                    "상품 runtime cache 삭제 실패",
                    e
            );
        }
    }

    @Override
    public void evictAll(Collection<Long> productIds) {
        List<String> keys = normalizeIds(productIds).stream()
                .map(keyGenerator::runtimeKey)
                .toList();

        if (keys.isEmpty()) {
            return;
        }

        try {
            circuitBreaker.executeRunnable(() -> redisTemplate.delete(keys));
            log.debug("event=cache_evict_all_success cache={} keyCount={}", CACHE_NAME, keys.size());
        } catch (DataAccessException | CallNotPermittedException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "evictAll",
                    keys.size(),
                    "상품 runtime cache 일괄 삭제 실패",
                    e
            );
        }
    }

    private ValueOperations<String, String> valueOperations() {
        return redisTemplate.opsForValue();
    }

    private boolean isExpectedPayload(Long requestedProductId, ProductRuntimeCacheData runtimeData) {
        return runtimeData != null
                && runtimeData.productId() != null
                && requestedProductId.equals(runtimeData.productId());
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

    private void deleteQuietly(String key, Long productId) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn(
                    "event=cache_cleanup_error cache={} key={} productId={} message={}",
                    CACHE_NAME, key, productId, e.getMessage()
            );
        }
    }
}
