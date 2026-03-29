package com.product.presentation.common.response;

public record ApiErrorDetail(
        String field,
        String reason,
        Object rejectedValue
) {
}

