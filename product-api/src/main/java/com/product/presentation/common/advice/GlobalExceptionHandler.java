package com.product.presentation.common.advice;

import com.product.domain.common.exception.CommonErrorCode;
import com.product.domain.common.exception.DomainException;
import com.product.presentation.common.response.ApiErrorDetail;
import com.product.presentation.common.response.ApiErrorResponse;
import com.product.presentation.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(
            DomainException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getErrorCode().getStatus());

        return ResponseEntity.status(status)
                .body(ApiResponse.failure(
                        ApiErrorResponse.of(exception.getErrorCode(), exception.getMessage()),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorDetail(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()
                ))
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(
                        ApiErrorResponse.of(CommonErrorCode.VALIDATION_ERROR, details),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        violation.getInvalidValue()
                ))
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(
                        ApiErrorResponse.of(CommonErrorCode.INVALID_INPUT, details),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(
                        ApiErrorResponse.of(CommonErrorCode.MESSAGE_NOT_READABLE),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.internalServerError()
                .body(ApiResponse.failure(
                        ApiErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR),
                        request.getRequestURI()
                ));
    }
}