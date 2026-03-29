package com.product.domain.product.model;

import com.product.domain.product.exception.ProductErrorCode;
import com.product.domain.product.exception.ProductException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
public class Product {
    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final Integer stock;
    private final Instant updatedAt;

    public Product(Long id, String name, BigDecimal price, Integer stock, Instant updatedAt) {
        validate(id, name, price, stock, updatedAt);
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.updatedAt = updatedAt;
    }

    public static Product of(Long id, String name, BigDecimal price, Integer stock, Instant updatedAt) {
        return new Product(id, name, price, stock, updatedAt);
    }

    public ProductAvailability availability() {
        return ProductAvailability.from(this.stock);
    }

    private void validate(Long id, String name, BigDecimal price, Integer stock, Instant updatedAt) {
        if (id == null || id < 1) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_ID);
        }
        if (name == null || name.isBlank()) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_NAME);
        }
        if (price == null || price.signum() < 0) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_PRICE);
        }
        if (stock != null && stock < 0) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_STOCK);
        }
        if (updatedAt == null) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_UPDATED_AT);
        }
    }

}