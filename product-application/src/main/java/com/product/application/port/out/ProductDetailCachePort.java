package com.product.application.port.out;

import com.product.application.dto.result.ProductResult;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ProductDetailCachePort {

    Optional<ProductResult> get(Long productId);

    Map<Long, ProductResult> getAll(Collection<Long> productIds);

    void put(ProductResult product);

    void putAll(Collection<ProductResult> products);

    void evict(Long productId);

    void evictAll(Collection<Long> productIds);
}