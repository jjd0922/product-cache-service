package com.product.domain.product.exception;

import com.product.domain.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND("PRODUCT_404", "상품을 찾을 수 없습니다.", 404),

    INVALID_PRODUCT_ID("PRODUCT_400_1", "상품 ID는 1 이상이어야 합니다.", 400),
    INVALID_PRODUCT_NAME("PRODUCT_400_2", "상품명은 비어 있을 수 없습니다.", 400),
    INVALID_PRODUCT_PRICE("PRODUCT_400_3", "상품 가격은 0 이상이어야 합니다.", 400),
    INVALID_PRODUCT_STOCK("PRODUCT_400_4", "상품 재고는 0 이상이어야 합니다.", 400),
    INVALID_PRODUCT_UPDATED_AT("PRODUCT_400_5", "상품 수정일시는 null일 수 없습니다.", 400);


    private final String code;
    private final String message;
    private final int status;
}