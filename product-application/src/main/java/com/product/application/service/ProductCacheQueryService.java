package com.product.application.service;

import com.product.application.dto.result.ProductResult;
import com.product.application.port.out.ProductReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheQueryService {

    private final ProductReadPort productReadPort;

    public Optional<ProductResult> getProduct(Long productId) {
        if (!isValidProductId(productId)) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    public List<ProductResult> getProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return List.of();
    }

    private boolean isValidProductId(Long productId) {
        return productId != null && productId > 0;
    }
}