package com.product.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    NOT_FOUND("COMMON-404", "리소스를 찾을 수 없습니다.", 404),
    INVALID_INPUT("COMMON-400", "잘못된 입력입니다.", 400),
    VALIDATION_ERROR("COMMON-400-VALIDATION", "요청 값 검증에 실패했습니다.", 400),
    MESSAGE_NOT_READABLE("COMMON-400-JSON", "잘못된 요청 본문입니다.", 400),
    SERVICE_UNAVAILABLE("COMMON-503", "서비스를 일시적으로 사용할 수 없습니다.", 503),
    INTERNAL_SERVER_ERROR("COMMON-500", "서버 내부 오류가 발생했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;
}
