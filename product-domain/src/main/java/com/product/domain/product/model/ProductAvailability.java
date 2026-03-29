package com.product.domain.product.model;

import com.product.domain.product.exception.ProductErrorCode;
import com.product.domain.product.exception.ProductException;

public record ProductAvailability(Integer stock) {

    public ProductAvailability {
        if (stock != null && stock < 0) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_STOCK);
        }
    }

    public static ProductAvailability from(Integer stock) {
        return new ProductAvailability(stock);
    }

    public boolean isSoldOut() {
        return stock != null && stock == 0;
    }

    public ProductDisplayStatus displayStatus() {
        if (stock == null) {
            return ProductDisplayStatus.UNKNOWN;
        }
        return isSoldOut() ? ProductDisplayStatus.SOLD_OUT : ProductDisplayStatus.ON_SALE;
    }
}