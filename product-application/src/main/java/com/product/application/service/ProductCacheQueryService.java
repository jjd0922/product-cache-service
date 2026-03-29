package com.product.application.service;

import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.application.dto.result.ProductResult;
import com.product.application.factory.ProductResultFactory;
import com.product.application.factory.ProductRuntimeCacheFactory;
import com.product.application.port.out.ProductDetailCachePort;
import com.product.application.port.out.ProductReadPort;
import com.product.application.port.out.ProductRuntimeCachePort;
import com.product.domain.product.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheQueryService {

    private final ProductReadPort productReadPort;
    private final ProductDetailCachePort productDetailCachePort;
    private final ProductRuntimeCachePort productRuntimeCachePort;
    private final ProductResultFactory productResultFactory;
    private final ProductRuntimeCacheFactory productRuntimeCacheFactory;

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

        Map<Long, ProductResult> detailCacheMap = safeGetDetailCacheAll(distinctIds);
        Map<Long, ProductRuntimeCacheData> runtimeCacheMap = safeGetRuntimeCacheAll(distinctIds);

        Map<Long, ProductResult> resultMap = merge(detailCacheMap, runtimeCacheMap, distinctIds);

        List<Long> fallbackIds = distinctIds.stream()
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

        List<Product> productsFromDb = productReadPort.findAllByIdIn(fallbackIds);
        if (productsFromDb.isEmpty()) {
            return resultMap;
        }

        List<ProductResult> detailFallbacks = new ArrayList<>(productsFromDb.size());
        List<ProductRuntimeCacheData> runtimeFallbacks = new ArrayList<>(productsFromDb.size());

        for (Product product : productsFromDb) {
            if (!isValidProduct(product)) {
                continue;
            }

            Long productId = product.getId();

            ProductResult detail = detailCacheMap.get(productId);
            if (detail == null) {
                detail = productResultFactory.from(product);
            }

            if (detail == null || detail.id() == null) {
                continue;
            }

            ProductRuntimeCacheData runtime = runtimeCacheMap.get(productId);
            if (runtime == null) {
                runtime = createRuntime(product);
            }

            if (!detailCacheMap.containsKey(productId)) {
                detailFallbacks.add(detail);
            }

            if (runtime != null && !runtimeCacheMap.containsKey(productId)) {
                runtimeFallbacks.add(runtime);
            }

            resultMap.put(productId, detail.applyRuntime(runtime));
        }

        safePutDetailCacheAll(detailFallbacks);
        safePutRuntimeCacheAll(runtimeFallbacks);

        return resultMap;
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
            return productDetailCachePort.getAll(productIds);
        } catch (RuntimeException e) {
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
            return productRuntimeCachePort.getAll(productIds);
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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