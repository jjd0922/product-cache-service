package com.product.presentation.controller;

import com.product.application.dto.result.ProductResult;
import com.product.application.port.in.ProductQueryUseCase;
import com.product.domain.product.exception.ProductErrorCode;
import com.product.domain.product.exception.ProductException;
import com.product.presentation.assembler.ProductResponseAssembler;
import com.product.presentation.common.response.ApiResponse;
import com.product.presentation.dto.request.IdsRequest;
import com.product.presentation.dto.response.ProductResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductQueryUseCase productQueryUseCase;
    private final ProductResponseAssembler productResponseAssembler;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable
            @Min(value = 1, message = "productId는 1 이상의 값이어야 합니다.")
            Long productId,
            HttpServletRequest request
    ) {
        ProductResponse response = productQueryUseCase.getProduct(productId)
                .map(productResponseAssembler::from)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return ResponseEntity.ok(ApiResponse.success(response, request.getRequestURI()));
    }

    @PostMapping("/ids")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProducts(
            @RequestBody(required = false) @Valid IdsRequest request,
            HttpServletRequest httpServletRequest
    ) {
        List<Long> ids = request != null && request.ids() != null ? request.ids() : List.of();

        List<ProductResult> results = productQueryUseCase.getProducts(ids);
        List<ProductResponse> responses = results.stream()
                .map(productResponseAssembler::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses, httpServletRequest.getRequestURI()));
    }
}