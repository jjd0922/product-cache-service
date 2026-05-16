package com.product.application.service;

import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.application.dto.result.ProductResult;
import com.product.application.factory.ProductResultFactory;
import com.product.application.factory.ProductRuntimeCacheFactory;
import com.product.application.port.out.ProductDetailCachePort;
import com.product.application.port.out.ProductNotFoundCachePort;
import com.product.application.port.out.ProductRuntimeCachePort;
import com.product.domain.product.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCacheRefreshService {

    private final ProductDetailCachePort productDetailCachePort;
    private final ProductRuntimeCachePort productRuntimeCachePort;
    private final ProductNotFoundCachePort productNotFoundCachePort;
    private final ProductResultFactory productResultFactory;
    private final ProductRuntimeCacheFactory productRuntimeCacheFactory;

    public void refresh(Product product) {
        if (!isValidProduct(product)) {
            return;
        }

        ProductResult detail = toDetail(product);
        ProductRuntimeCacheData runtime = toRuntime(product);

        productNotFoundCachePort.evict(product.getId());

        if (detail != null) {
            productDetailCachePort.put(detail);
        }

        if (runtime != null) {
            productRuntimeCachePort.put(runtime);
        }
    }

    public void refreshAll(Collection<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        List<ProductResult> detailResults = new ArrayList<>();
        List<ProductRuntimeCacheData> runtimeResults = new ArrayList<>();

        for (Product product : products) {
            if (!isValidProduct(product)) {
                continue;
            }

            ProductResult detail = toDetail(product);
            ProductRuntimeCacheData runtime = toRuntime(product);

            if (detail != null) {
                detailResults.add(detail);
            }

            if (runtime != null) {
                runtimeResults.add(runtime);
            }
        }

        productNotFoundCachePort.evictAll(validProductIds(products));

        if (!detailResults.isEmpty()) {
            productDetailCachePort.putAll(detailResults);
        }

        if (!runtimeResults.isEmpty()) {
            productRuntimeCachePort.putAll(runtimeResults);
        }
    }

    public void evict(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }

        productDetailCachePort.evict(productId);
        productRuntimeCachePort.evict(productId);
        productNotFoundCachePort.evict(productId);
    }

    public void evictAll(Collection<Long> productIds) {
        List<Long> validIds = normalizeIds(productIds);
        if (validIds.isEmpty()) {
            return;
        }

        productDetailCachePort.evictAll(validIds);
        productRuntimeCachePort.evictAll(validIds);
        productNotFoundCachePort.evictAll(validIds);
    }

    private List<Long> validProductIds(Collection<Product> products) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Product product : products) {
            if (isValidProduct(product)) {
                ids.add(product.getId());
            }
        }
        return new ArrayList<>(ids);
    }

    private ProductResult toDetail(Product product) {
        return productResultFactory.from(product);
    }

    private ProductRuntimeCacheData toRuntime(Product product) {
        return productRuntimeCacheFactory.from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        );
    }

    private List<Long> normalizeIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> validIds = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (isValidProductId(productId)) {
                validIds.add(productId);
            }
        }
        return new ArrayList<>(validIds);
    }

    private boolean isValidProduct(Product product) {
        return product != null && isValidProductId(product.getId());
    }

    private boolean isValidProductId(Long productId) {
        return productId != null && productId > 0;
    }
}
