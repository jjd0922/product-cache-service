package com.product.application.common.exception;

import lombok.Getter;

@Getter
public class DbFallbackRejectedException extends RuntimeException {

    private final int requestedCount;

    public DbFallbackRejectedException(int requestedCount, Throwable cause) {
        super("DB fallback concurrency limit exceeded.", cause);
        this.requestedCount = requestedCount;
    }
}
