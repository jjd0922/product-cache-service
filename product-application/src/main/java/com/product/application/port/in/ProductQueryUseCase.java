package com.product.application.port.in;

import com.product.application.dto.result.ProductResult;

import java.util.List;
import java.util.Optional;

public interface ProductQueryUseCase {
    Optional<ProductResult> getProduct(Long productId);
    List<ProductResult> getProducts(List<Long> productIds);
}
