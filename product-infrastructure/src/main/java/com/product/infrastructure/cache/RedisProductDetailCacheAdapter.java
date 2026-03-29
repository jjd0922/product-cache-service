package com.product.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.application.common.exception.CacheOperationException;
import com.product.application.dto.result.ProductResult;
import com.product.application.port.out.ProductDetailCachePort;
import com.product.infrastructure.cache.support.ProductCacheKeyGenerator;
import com.product.infrastructure.cache.support.RedisCacheBatchExecutor;
import com.product.infrastructure.config.ProductCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class RedisProductDetailCacheAdapter implements ProductDetailCachePort {

    private static final String CACHE_NAME = "product-detail-cache";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductCacheProperties properties;
    private final ProductCacheKeyGenerator keyGenerator;
    private final RedisCacheBatchExecutor batchExecutor;

    public RedisProductDetailCacheAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ProductCacheProperties properties,
            ProductCacheKeyGenerator keyGenerator,
            RedisCacheBatchExecutor batchExecutor
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.keyGenerator = keyGenerator;
        this.batchExecutor = batchExecutor;
    }

    @Override
    public Optional<ProductResult> get(Long productId) {
        if (!isValidProductId(productId)) {
            return Optional.empty();
        }

        String key = keyGenerator.detailKey(productId);

        try {
            String cached = valueOperations().get(key);
            if (cached == null) {
                log.debug("event=cache_get_miss cache={} key={} productId={}", CACHE_NAME, key, productId);
                return Optional.empty();
            }

            ProductResult product = objectMapper.readValue(cached, ProductResult.class);
            if (!isExpectedPayload(productId, product)) {
                log.warn(
                        "event=cache_get_id_mismatch cache={} key={} requestedProductId={} payloadProductId={}",
                        CACHE_NAME,
                        key,
                        productId,
                        product != null ? product.id() : null
                );
                deleteQuietly(key, productId);
                return Optional.empty();
            }

            log.debug("event=cache_get_hit cache={} key={} productId={}", CACHE_NAME, key, productId);
            return Optional.of(product);
        } catch (JsonProcessingException e) {
            log.warn(
                    "event=cache_get_deserialize_error cache={} key={} productId={} message={}",
                    CACHE_NAME, key, productId, e.getMessage()
            );
            deleteQuietly(key, productId);
            return Optional.empty();
        } catch (DataAccessException e) {
            log.warn(
                    "event=cache_get_error cache={} key={} productId={} message={}",
                    CACHE_NAME, key, productId, e.getMessage()
            );
            return Optional.empty();
        }
    }

    @Override
    public Map<Long, ProductResult> getAll(Collection<Long> productIds) {
        List<Long> validIds = normalizeIds(productIds);
        if (validIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = validIds.stream()
                .map(keyGenerator::detailKey)
                .toList();

        try {
            List<String> cachedValues = valueOperations().multiGet(keys);
            if (cachedValues == null || cachedValues.isEmpty()) {
                return Map.of();
            }

            Map<Long, ProductResult> results = new LinkedHashMap<>();

            for (int i = 0; i < keys.size(); i++) {
                String cached = cachedValues.get(i);
                if (cached == null) {
                    continue;
                }

                Long productId = validIds.get(i);
                String key = keys.get(i);

                try {
                    ProductResult product = objectMapper.readValue(cached, ProductResult.class);
                    if (!isExpectedPayload(productId, product)) {
                        log.warn(
                                "event=cache_batch_get_id_mismatch cache={} key={} requestedProductId={} payloadProductId={}",
                                CACHE_NAME,
                                key,
                                productId,
                                product != null ? product.id() : null
                        );
                        deleteQuietly(key, productId);
                        continue;
                    }

                    results.put(productId, product);
                } catch (JsonProcessingException e) {
                    log.warn(
                            "event=cache_batch_get_deserialize_error cache={} key={} productId={} message={}",
                            CACHE_NAME, key, productId, e.getMessage()
                    );
                    deleteQuietly(key, productId);
                }
            }

            return results;
        } catch (DataAccessException e) {
            log.warn(
                    "event=cache_batch_get_error cache={} keyCount={} message={}",
                    CACHE_NAME, keys.size(), e.getMessage()
            );
            return Map.of();
        }
    }

    @Override
    public void put(ProductResult product) {
        if (product == null || !isValidProductId(product.id())) {
            return;
        }

        String key = keyGenerator.detailKey(product.id());

        try {
            String payload = objectMapper.writeValueAsString(product);
            valueOperations().set(key, payload, Duration.ofSeconds(properties.getDetailTtlSeconds()));
            log.debug("event=cache_put_success cache={} key={} productId={}", CACHE_NAME, key, product.id());
        } catch (JsonProcessingException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "put",
                    1,
                    "상품 detail cache 직렬화 실패",
                    e
            );
        } catch (DataAccessException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "put",
                    1,
                    "상품 detail cache 저장 실패",
                    e
            );
        }
    }

    @Override
    public void putAll(Collection<ProductResult> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        List<ProductResult> validProducts = products.stream()
                .filter(Objects::nonNull)
                .filter(product -> isValidProductId(product.id()))
                .toList();

        if (validProducts.isEmpty()) {
            return;
        }

        int batchSize = properties.getPipelineBatchSize();
        if (batchSize < 1) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "putAll",
                    validProducts.size(),
                    "pipelineBatchSize 는 1 이상이어야 합니다"
            );
        }

        List<RedisCacheBatchExecutor.CacheEntry> entries = new ArrayList<>(validProducts.size());
        try {
            for (ProductResult product : validProducts) {
                entries.add(new RedisCacheBatchExecutor.CacheEntry(
                        keyGenerator.detailKey(product.id()),
                        objectMapper.writeValueAsString(product)
                ));
            }
        } catch (JsonProcessingException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "putAll",
                    validProducts.size(),
                    "상품 detail cache 배치 직렬화 실패",
                    e
            );
        }

        for (int start = 0; start < entries.size(); start += batchSize) {
            List<RedisCacheBatchExecutor.CacheEntry> batch = entries.subList(
                    start,
                    Math.min(start + batchSize, entries.size())
            );

            try {
                batchExecutor.setExBatch(batch, properties.getDetailTtlSeconds());
            } catch (RuntimeException e) {
                throw new CacheOperationException(
                        CACHE_NAME,
                        "putAll",
                        batch.size(),
                        "상품 detail cache pipeline 저장 실패",
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

        String key = keyGenerator.detailKey(productId);

        try {
            redisTemplate.delete(key);
            log.debug("event=cache_evict_success cache={} key={} productId={}", CACHE_NAME, key, productId);
        } catch (DataAccessException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "evict",
                    1,
                    "상품 detail cache 삭제 실패",
                    e
            );
        }
    }

    @Override
    public void evictAll(Collection<Long> productIds) {
        List<String> keys = normalizeIds(productIds).stream()
                .map(keyGenerator::detailKey)
                .toList();

        if (keys.isEmpty()) {
            return;
        }

        try {
            redisTemplate.delete(keys);
            log.debug("event=cache_evict_all_success cache={} keyCount={}", CACHE_NAME, keys.size());
        } catch (DataAccessException e) {
            throw new CacheOperationException(
                    CACHE_NAME,
                    "evictAll",
                    keys.size(),
                    "상품 detail cache 일괄 삭제 실패",
                    e
            );
        }
    }

    private ValueOperations<String, String> valueOperations() {
        return redisTemplate.opsForValue();
    }

    private boolean isExpectedPayload(Long requestedProductId, ProductResult product) {
        return product != null
                && product.id() != null
                && requestedProductId.equals(product.id());
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