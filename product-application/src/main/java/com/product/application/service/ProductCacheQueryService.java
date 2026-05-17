package com.product.application.service;

import com.product.application.common.exception.DbFallbackRejectedException;
import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.application.dto.result.ProductResult;
import com.product.application.factory.ProductResultFactory;
import com.product.application.factory.ProductRuntimeCacheFactory;
import com.product.application.port.out.ProductCacheMetricsPort;
import com.product.application.port.out.ProductCacheSingleFlightLock;
import com.product.application.port.out.ProductCacheSingleFlightLockPort;
import com.product.application.port.out.ProductDetailCachePort;
import com.product.application.port.out.ProductNotFoundCachePort;
import com.product.application.port.out.ProductReadPort;
import com.product.application.port.out.ProductRuntimeCachePort;
import com.product.domain.product.model.Product;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheQueryService {

    private static final String DETAIL_CACHE = "detail";
    private static final String RUNTIME_CACHE = "runtime";
    private static final int LOCK_WAIT_RETRY_COUNT = 5;
    private static final long LOCK_WAIT_BACKOFF_MILLIS = 50L;

    private final ProductReadPort productReadPort;
    private final ProductDetailCachePort productDetailCachePort;
    private final ProductRuntimeCachePort productRuntimeCachePort;
    private final ProductNotFoundCachePort productNotFoundCachePort;
    private final ProductCacheMetricsPort productCacheMetricsPort;
    private final ProductCacheSingleFlightLockPort productCacheSingleFlightLockPort;
    private final ProductResultFactory productResultFactory;
    private final ProductRuntimeCacheFactory productRuntimeCacheFactory;
    private final ConcurrentHashMap<Long, CompletableFuture<Optional<ProductResult>>> localInFlight =
            new ConcurrentHashMap<>();
    private volatile Bulkhead dbFallbackBulkhead;

    @Value("${product.cache.enabled:true}")
    private boolean cacheEnabled = true;

    @Value("${product.cache.fallback.max-concurrent:20}")
    private int dbFallbackMaxConcurrentCalls = 20;

    @Value("${product.cache.fallback.max-wait-millis:0}")
    private long dbFallbackMaxWaitMillis = 0L;

    public Optional<ProductResult> getProduct(Long productId) {
        if (!isValidProductId(productId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getProductsAsMap(List.of(productId)).get(productId));
    }

    public List<ProductResult> getProducts(List<Long> productIds) {
        List<Long> distinctIds = normalizeIds(productIds);
        if (distinctIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductResult> resultMap = getProductsAsMap(distinctIds);

        List<ProductResult> orderedResults = new ArrayList<>(resultMap.size());
        for (Long productId : distinctIds) {
            ProductResult result = resultMap.get(productId);
            if (result != null) {
                orderedResults.add(result);
            }
        }
        return orderedResults;
    }

    private Map<Long, ProductResult> getProductsAsMap(Collection<Long> productIds) {
        List<Long> distinctIds = normalizeIds(productIds);
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        if (!cacheEnabled) {
            return getProductsFromDbOnly(distinctIds);
        }

        Set<Long> notFoundCacheHits = safeGetNotFoundCacheAll(distinctIds);
        List<Long> lookupIds = distinctIds.stream()
                .filter(productId -> !notFoundCacheHits.contains(productId))
                .toList();

        if (lookupIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ProductResult> detailCacheMap = safeGetDetailCacheAll(lookupIds);
        Map<Long, ProductRuntimeCacheData> runtimeCacheMap = safeGetRuntimeCacheAll(lookupIds);

        Map<Long, ProductResult> resultMap = merge(detailCacheMap, runtimeCacheMap, lookupIds);

        List<Long> fallbackIds = lookupIds.stream()
                .filter(productId -> !detailCacheMap.containsKey(productId) || !runtimeCacheMap.containsKey(productId))
                .toList();

        if (fallbackIds.isEmpty()) {
            return resultMap;
        }

        long detailMissCount = fallbackIds.stream()
                .filter(productId -> !detailCacheMap.containsKey(productId))
                .count();

        long runtimeMissCount = fallbackIds.stream()
                .filter(productId -> !runtimeCacheMap.containsKey(productId))
                .count();

        log.debug(
                "상품 cache miss. DB fallback 수행. fallbackCount={}, detailMissCount={}, runtimeMissCount={}",
                fallbackIds.size(),
                detailMissCount,
                runtimeMissCount
        );

        for (Long fallbackId : fallbackIds) {
            loadProductWithSingleFlight(fallbackId)
                    .ifPresent(result -> resultMap.put(fallbackId, result));
        }

        return resultMap;
    }

    private Map<Long, ProductResult> getProductsFromDbOnly(List<Long> productIds) {
        List<Product> productsFromDb = findProductsWithDbFallbackBulkhead(productIds);
        productCacheMetricsPort.recordDbFallback(productIds.size(), productsFromDb.size());

        Map<Long, ProductResult> resultMap = new LinkedHashMap<>();
        for (Product product : productsFromDb) {
            if (!isValidProduct(product)) {
                continue;
            }

            ProductResult detail = productResultFactory.from(product);
            if (detail == null || detail.id() == null) {
                continue;
            }

            resultMap.put(product.getId(), detail.applyRuntime(createRuntime(product)));
        }
        return resultMap;
    }

    private Optional<ProductResult> loadProductWithSingleFlight(Long productId) {
        if (isNotFoundCached(productId)) {
            return Optional.empty();
        }

        CompletableFuture<Optional<ProductResult>> current = new CompletableFuture<>();
        CompletableFuture<Optional<ProductResult>> existing = localInFlight.putIfAbsent(productId, current);
        if (existing != null) {
            return join(existing);
        }

        try {
            Optional<ProductResult> result = loadProductWithDistributedLock(productId);
            current.complete(result);
            return result;
        } catch (RuntimeException e) {
            current.completeExceptionally(e);
            throw e;
        } finally {
            localInFlight.remove(productId, current);
        }
    }

    private Optional<ProductResult> loadProductWithDistributedLock(Long productId) {
        Optional<ProductCacheSingleFlightLock> lock;
        try {
            lock = productCacheSingleFlightLockPort.tryLock(productId);
        } catch (RuntimeException e) {
            log.warn("single-flight lock 획득 실패. DB fallback 으로 degrade. productId={}, message={}", productId, e.getMessage());
            return loadProductFromCacheOrDb(productId);
        }

        if (lock.isPresent()) {
            try (ProductCacheSingleFlightLock ignored = lock.get()) {
                return loadProductFromCacheOrDb(productId);
            }
        }

        Optional<ProductResult> cached = waitAndGetFullyCachedProduct(productId);
        if (cached.isPresent()) {
            return cached;
        }

        log.debug("single-flight lock 획득 실패 후 캐시 재조회 miss. DB fallback 수행. productId={}", productId);
        return loadProductFromCacheOrDb(productId);
    }

    private Optional<ProductResult> waitAndGetFullyCachedProduct(Long productId) {
        for (int i = 0; i < LOCK_WAIT_RETRY_COUNT; i++) {
            sleepBeforeCacheRetry();

            if (isNotFoundCached(productId)) {
                return Optional.empty();
            }

            Optional<ProductResult> cached = getFullyCachedProduct(productId);
            if (cached.isPresent()) {
                return cached;
            }
        }
        return Optional.empty();
    }

    private void sleepBeforeCacheRetry() {
        try {
            Thread.sleep(LOCK_WAIT_BACKOFF_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<ProductResult> loadProductFromCacheOrDb(Long productId) {
        if (isNotFoundCached(productId)) {
            return Optional.empty();
        }

        Optional<ProductResult> cached = getFullyCachedProduct(productId);
        if (cached.isPresent()) {
            return cached;
        }

        List<Product> productsFromDb = findProductsWithDbFallbackBulkhead(List.of(productId));
        Product product = productsFromDb.stream()
                .filter(this::isValidProduct)
                .filter(candidate -> productId.equals(candidate.getId()))
                .findFirst()
                .orElse(null);

        productCacheMetricsPort.recordDbFallback(1L, product == null ? 0L : 1L);
        if (product == null) {
            safePutNotFoundCache(productId);
            return Optional.empty();
        }

        return Optional.ofNullable(repopulateMissingCaches(productId, product));
    }

    private List<Product> findProductsWithDbFallbackBulkhead(List<Long> productIds) {
        try {
            return getDbFallbackBulkhead().executeSupplier(() -> productReadPort.findAllByIdIn(productIds));
        } catch (BulkheadFullException e) {
            productCacheMetricsPort.recordDbFallbackRejected(productIds.size());
            log.warn(
                    "DB fallback bulkhead rejected. requestedCount={}, maxConcurrentCalls={}",
                    productIds.size(),
                    Math.max(dbFallbackMaxConcurrentCalls, 1)
            );
            throw new DbFallbackRejectedException(productIds.size(), e);
        }
    }

    private Bulkhead getDbFallbackBulkhead() {
        Bulkhead current = dbFallbackBulkhead;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (dbFallbackBulkhead == null) {
                BulkheadConfig config = BulkheadConfig.custom()
                        .maxConcurrentCalls(Math.max(dbFallbackMaxConcurrentCalls, 1))
                        .maxWaitDuration(Duration.ofMillis(Math.max(dbFallbackMaxWaitMillis, 0L)))
                        .build();
                dbFallbackBulkhead = Bulkhead.of("product-cache-db-fallback", config);
            }
            return dbFallbackBulkhead;
        }
    }

    private Optional<ProductResult> getFullyCachedProduct(Long productId) {
        Map<Long, ProductResult> detailCacheMap = safeGetDetailCacheAll(List.of(productId));
        Map<Long, ProductRuntimeCacheData> runtimeCacheMap = safeGetRuntimeCacheAll(List.of(productId));

        if (!detailCacheMap.containsKey(productId) || !runtimeCacheMap.containsKey(productId)) {
            return Optional.empty();
        }

        ProductResult detail = detailCacheMap.get(productId);
        ProductRuntimeCacheData runtime = runtimeCacheMap.get(productId);
        if (detail == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(detail.applyRuntime(runtime));
    }

    private boolean isNotFoundCached(Long productId) {
        return safeGetNotFoundCacheAll(List.of(productId)).contains(productId);
    }

    private ProductResult repopulateMissingCaches(Long productId, Product product) {
        Map<Long, ProductResult> detailCacheMap = safeGetDetailCacheAll(List.of(productId));
        Map<Long, ProductRuntimeCacheData> runtimeCacheMap = safeGetRuntimeCacheAll(List.of(productId));

        ProductResult detail = detailCacheMap.get(productId);
        if (detail == null) {
            detail = productResultFactory.from(product);
        }

        if (detail == null || detail.id() == null) {
            return null;
        }

        ProductRuntimeCacheData runtime = runtimeCacheMap.get(productId);
        if (runtime == null) {
            runtime = createRuntime(product);
        }

        if (!detailCacheMap.containsKey(productId)) {
            safePutDetailCacheAll(List.of(detail));
        }

        if (runtime != null && !runtimeCacheMap.containsKey(productId)) {
            safePutRuntimeCacheAll(List.of(runtime));
        }

        return detail.applyRuntime(runtime);
    }

    private Optional<ProductResult> join(CompletableFuture<Optional<ProductResult>> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Set<Long> safeGetNotFoundCacheAll(Collection<Long> productIds) {
        try {
            Set<Long> results = productNotFoundCachePort.getAll(productIds);
            productCacheMetricsPort.recordNotFoundCacheHit(results.size());
            return results;
        } catch (RuntimeException e) {
            log.warn(
                    "상품 not-found cache 조회 실패. 일반 조회 흐름으로 진행. idCount={}, message={}",
                    productIds.size(),
                    e.getMessage()
            );
            return Set.of();
        }
    }

    private void safePutNotFoundCache(Long productId) {
        try {
            productNotFoundCachePort.put(productId);
        } catch (RuntimeException e) {
            log.warn("DB fallback 결과 없음. not-found cache 저장 실패. productId={}, message={}", productId, e.getMessage());
        }
    }

    private Map<Long, ProductResult> merge(
            Map<Long, ProductResult> detailCacheMap,
            Map<Long, ProductRuntimeCacheData> runtimeCacheMap,
            List<Long> productIds
    ) {
        Map<Long, ProductResult> resultMap = new LinkedHashMap<>();

        for (Long productId : productIds) {
            ProductResult detail = detailCacheMap.get(productId);
            if (detail == null) {
                continue;
            }

            ProductRuntimeCacheData runtime = runtimeCacheMap.get(productId);
            resultMap.put(productId, detail.applyRuntime(runtime));
        }

        return resultMap;
    }

    private ProductRuntimeCacheData createRuntime(Product product) {
        return productRuntimeCacheFactory.from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        );
    }

    private Map<Long, ProductResult> safeGetDetailCacheAll(Collection<Long> productIds) {
        try {
            Map<Long, ProductResult> results = productDetailCachePort.getAll(productIds);
            productCacheMetricsPort.recordCacheRead(DETAIL_CACHE, productIds.size(), results.size(), false);
            return results;
        } catch (RuntimeException e) {
            productCacheMetricsPort.recordCacheRead(DETAIL_CACHE, productIds.size(), 0L, true);
            log.warn(
                    "상품 detail cache 일괄 조회 실패. DB fallback 예정. idCount={}, message={}",
                    productIds.size(),
                    e.getMessage()
            );
            return Map.of();
        }
    }

    private Map<Long, ProductRuntimeCacheData> safeGetRuntimeCacheAll(Collection<Long> productIds) {
        try {
            Map<Long, ProductRuntimeCacheData> results = productRuntimeCachePort.getAll(productIds);
            productCacheMetricsPort.recordCacheRead(RUNTIME_CACHE, productIds.size(), results.size(), false);
            return results;
        } catch (RuntimeException e) {
            productCacheMetricsPort.recordCacheRead(RUNTIME_CACHE, productIds.size(), 0L, true);
            log.warn(
                    "상품 runtime cache 일괄 조회 실패. runtime cache 없이 진행. idCount={}, message={}",
                    productIds.size(),
                    e.getMessage()
            );
            return Map.of();
        }
    }

    private void safePutDetailCacheAll(Collection<ProductResult> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        try {
            productDetailCachePort.putAll(products);
            productCacheMetricsPort.recordCacheWrite(DETAIL_CACHE, products.size(), false);
        } catch (RuntimeException e) {
            productCacheMetricsPort.recordCacheWrite(DETAIL_CACHE, products.size(), true);
            log.warn(
                    "DB fallback 이후 상품 detail cache 일괄 재적재 실패. count={}, message={}",
                    products.size(),
                    e.getMessage()
            );
        }
    }

    private void safePutRuntimeCacheAll(Collection<ProductRuntimeCacheData> runtimeDataList) {
        if (runtimeDataList == null || runtimeDataList.isEmpty()) {
            return;
        }

        try {
            productRuntimeCachePort.putAll(runtimeDataList);
            productCacheMetricsPort.recordCacheWrite(RUNTIME_CACHE, runtimeDataList.size(), false);
        } catch (RuntimeException e) {
            productCacheMetricsPort.recordCacheWrite(RUNTIME_CACHE, runtimeDataList.size(), true);
            log.warn(
                    "DB fallback 이후 상품 runtime cache 일괄 재적재 실패. count={}, message={}",
                    runtimeDataList.size(),
                    e.getMessage()
            );
        }
    }

    private List<Long> normalizeIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (isValidProductId(productId)) {
                normalized.add(productId);
            }
        }
        return new ArrayList<>(normalized);
    }

    private boolean isValidProduct(Product product) {
        return product != null && isValidProductId(product.getId());
    }

    private boolean isValidProductId(Long productId) {
        return productId != null && productId > 0;
    }
}
