package com.product.domain.common.exception;

public class NotFoundException extends DomainException {

    public NotFoundException() {
        super(CommonErrorCode.NOT_FOUND);
    }

    public NotFoundException(String message) {
        super(CommonErrorCode.NOT_FOUND, message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(CommonErrorCode.NOT_FOUND, message, cause);
    }
}