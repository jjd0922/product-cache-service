package com.product.presentation.common.response;

import com.product.domain.common.exception.ErrorCode;

import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<ApiErrorDetail> details
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, List.of());
    }

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                List.of()
        );
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                message,
                List.of()
        );
    }

    public static ApiErrorResponse of(ErrorCode errorCode, List<ApiErrorDetail> details) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                details
        );
    }
}