package com.product.application.port.out;

import java.util.Optional;

public interface ProductCacheSingleFlightLockPort {

    Optional<ProductCacheSingleFlightLock> tryLock(Long productId);
}
