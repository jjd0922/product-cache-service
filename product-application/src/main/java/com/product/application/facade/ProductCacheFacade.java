package com.product.application.facade;

import com.product.application.dto.result.ProductResult;
import com.product.application.port.in.ProductQueryUseCase;
import com.product.application.service.ProductCacheQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductCacheFacade implements ProductQueryUseCase {

    private final ProductCacheQueryService productCacheQueryService;

    @Override
    public Optional<ProductResult> getProduct(Long productId) {
        return productCacheQueryService.getProduct(productId);
    }

    @Override
    public List<ProductResult> getProducts(List<Long> productIds) {
        return productCacheQueryService.getProducts(productIds);
    }
}