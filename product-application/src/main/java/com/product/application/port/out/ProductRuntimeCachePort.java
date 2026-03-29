package com.product.application.port.out;

import com.product.application.dto.cache.ProductRuntimeCacheData;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ProductRuntimeCachePort {

    Optional<ProductRuntimeCacheData> get(Long productId);

    Map<Long, ProductRuntimeCacheData> getAll(Collection<Long> productIds);

    void put(ProductRuntimeCacheData runtimeCacheData);

    void putAll(Collection<ProductRuntimeCacheData> runtimeCacheDataList);

    void evict(Long productId);

    void evictAll(Collection<Long> productIds);
}