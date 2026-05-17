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
    INVALID_PRODUCT_UPDATED_AT("PRODUCT_400_5", "상품 수정일시는 null일 수 없습니다.", 400),

    INVALID_REBUILD_REQUEST("PRODUCT_CACHE_400_1", "재빌드 요청은 null일 수 없습니다.", 400),
    INVALID_REBUILD_RANGE("PRODUCT_CACHE_400_2", "productIdFrom과 productIdTo는 함께 지정해야 합니다.", 400),
    INVALID_REBUILD_RANGE_ORDER("PRODUCT_CACHE_400_3", "productIdFrom은 productIdTo보다 클 수 없습니다.", 400),
    REBUILD_JOB_ALREADY_RUNNING("PRODUCT_CACHE_409_1", "이미 진행 중인 캐시 재빌드 작업이 있습니다.", 409),
    REBUILD_REQUEST_LIMIT_EXCEEDED("PRODUCT_CACHE_400_4", "재빌드 요청 제한을 초과했습니다.", 400),
    REBUILD_JOB_NOT_FOUND("PRODUCT_CACHE_404_1", "재빌드 작업을 찾을 수 없습니다.", 404),
    EVENT_DLQ_NOT_FOUND("PRODUCT_CACHE_EVENT_404_1", "DLQ 이벤트를 찾을 수 없습니다.", 404);

    private final String code;
    private final String message;
    private final int status;
}
