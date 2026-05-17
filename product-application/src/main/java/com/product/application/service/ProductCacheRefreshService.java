package com.product.application.service;

import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.application.dto.result.ProductResult;
import com.product.application.factory.ProductResultFactory;
import com.product.application.factory.ProductRuntimeCacheFactory;
import com.product.application.port.out.ProductDetailCachePort;
import com.product.application.port.out.ProductNotFoundCachePort;
import com.product.application.port.out.ProductRuntimeCachePort;
import com.product.domain.product.model.Product;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ProductCacheRefreshService {

    private final ProductDetailCachePort productDetailCachePort;
    private final ProductRuntimeCachePort productRuntimeCachePort;
    private final ProductNotFoundCachePort productNotFoundCachePort;
    private final ProductResultFactory productResultFactory;
    private final ProductRuntimeCacheFactory productRuntimeCacheFactory;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

    public void refresh(Product product) {
        if (!isValidProduct(product)) {
            return;
        }

        ProductResult detail = toDetail(product);
        ProductRuntimeCacheData runtime = toRuntime(product);

        observe("cache.write", () -> {
            productNotFoundCachePort.evict(product.getId());
            return null;
        }, "cache", "notfound", "operation", "evict", "item.count", "1");

        if (detail != null) {
            observe("cache.write", () -> {
                productDetailCachePort.put(detail);
                return null;
            }, "cache", "detail", "operation", "put", "item.count", "1");
        }

        if (runtime != null) {
            observe("cache.write", () -> {
                productRuntimeCachePort.put(runtime);
                return null;
            }, "cache", "runtime", "operation", "put", "item.count", "1");
        }
    }

    public void refreshAll(Collection<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        List<ProductResult> detailResults = new ArrayList<>();
        List<ProductRuntimeCacheData> runtimeResults = new ArrayList<>();

        for (Product product : products) {
            if (!isValidProduct(product)) {
                continue;
            }

            ProductResult detail = toDetail(product);
            ProductRuntimeCacheData runtime = toRuntime(product);

            if (detail != null) {
                detailResults.add(detail);
            }

            if (runtime != null) {
                runtimeResults.add(runtime);
            }
        }

        List<Long> validProductIds = validProductIds(products);
        observe("cache.write", () -> {
            productNotFoundCachePort.evictAll(validProductIds);
            return null;
        }, "cache", "notfound", "operation", "evictAll", "item.count", String.valueOf(validProductIds.size()));

        if (!detailResults.isEmpty()) {
            observe("cache.write", () -> {
                productDetailCachePort.putAll(detailResults);
                return null;
            }, "cache", "detail", "operation", "putAll", "item.count", String.valueOf(detailResults.size()));
        }

        if (!runtimeResults.isEmpty()) {
            observe("cache.write", () -> {
                productRuntimeCachePort.putAll(runtimeResults);
                return null;
            }, "cache", "runtime", "operation", "putAll", "item.count", String.valueOf(runtimeResults.size()));
        }
    }

    public void evict(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }

        observe("cache.write", () -> {
            productDetailCachePort.evict(productId);
            productRuntimeCachePort.evict(productId);
            productNotFoundCachePort.evict(productId);
            return null;
        }, "operation", "evict", "item.count", "1");
    }

    public void evictAll(Collection<Long> productIds) {
        List<Long> validIds = normalizeIds(productIds);
        if (validIds.isEmpty()) {
            return;
        }

        observe("cache.write", () -> {
            productDetailCachePort.evictAll(validIds);
            productRuntimeCachePort.evictAll(validIds);
            productNotFoundCachePort.evictAll(validIds);
            return null;
        }, "operation", "evictAll", "item.count", String.valueOf(validIds.size()));
    }

    private List<Long> validProductIds(Collection<Product> products) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Product product : products) {
            if (isValidProduct(product)) {
                ids.add(product.getId());
            }
        }
        return new ArrayList<>(ids);
    }

    private ProductResult toDetail(Product product) {
        return productResultFactory.from(product);
    }

    private ProductRuntimeCacheData toRuntime(Product product) {
        return productRuntimeCacheFactory.from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        );
    }

    private List<Long> normalizeIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> validIds = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (isValidProductId(productId)) {
                validIds.add(productId);
            }
        }
        return new ArrayList<>(validIds);
    }

    private boolean isValidProduct(Product product) {
        return product != null && isValidProductId(product.getId());
    }

    private boolean isValidProductId(Long productId) {
        return productId != null && productId > 0;
    }

    private <T> T observe(String name, Supplier<T> supplier, String... lowCardinalityKeyValues) {
        Observation observation = Observation.createNotStarted(name, observationRegistry);
        for (int i = 0; i + 1 < lowCardinalityKeyValues.length; i += 2) {
            observation.lowCardinalityKeyValue(lowCardinalityKeyValues[i], lowCardinalityKeyValues[i + 1]);
        }
        return observation.observe(supplier);
    }
}
