package com.product.domain.product.exception;

import com.product.domain.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND("PRODUCT_404", "상품을 찾을 수 없습니다.", 404);

    private final String code;
    private final String message;
    private final int status;
}