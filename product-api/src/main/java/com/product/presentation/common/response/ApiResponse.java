package com.product.presentation.common.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiErrorResponse error,
        Instant timestamp,
        String path
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), null);
    }

    public static <T> ApiResponse<T> success(T data, String path) {
        return new ApiResponse<>(true, data, null, Instant.now(), path);
    }

    public static <T> ApiResponse<T> failure(ApiErrorResponse error) {
        return new ApiResponse<>(false, null, error, Instant.now(), null);
    }

    public static <T> ApiResponse<T> failure(ApiErrorResponse error, String path) {
        return new ApiResponse<>(false, null, error, Instant.now(), path);
    }
}