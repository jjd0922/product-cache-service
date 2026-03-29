package com.product.application.common.exception;

import lombok.Getter;

@Getter
public class CacheOperationException extends RuntimeException {

    private final String cacheName;
    private final String operation;
    private final int targetCount;

    public CacheOperationException(
            String cacheName,
            String operation,
            int targetCount,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.cacheName = cacheName;
        this.operation = operation;
        this.targetCount = targetCount;
    }

    public CacheOperationException(
            String cacheName,
            String operation,
            int targetCount,
            String message
    ) {
        super(message);
        this.cacheName = cacheName;
        this.operation = operation;
        this.targetCount = targetCount;
    }
}