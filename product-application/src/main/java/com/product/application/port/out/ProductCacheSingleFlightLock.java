package com.product.application.port.out;

public interface ProductCacheSingleFlightLock extends AutoCloseable {

    @Override
    void close();
}
