package com.product.application.port.out;

import java.util.Collection;
import java.util.Set;

public interface ProductNotFoundCachePort {

    Set<Long> getAll(Collection<Long> productIds);

    void put(Long productId);

    void evict(Long productId);

    void evictAll(Collection<Long> productIds);
}
