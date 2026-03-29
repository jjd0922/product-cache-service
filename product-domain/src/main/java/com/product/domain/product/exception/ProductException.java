package com.product.domain.product.exception;

import com.product.domain.common.exception.DomainException;

public class ProductException extends DomainException {

    public ProductException(ProductErrorCode errorCode) {
        super(errorCode);
    }

    public ProductException(ProductErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ProductException(ProductErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}