package com.product.domain.product.model;

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
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.updatedAt = updatedAt;
    }

    public static Product of(Long id, String name, BigDecimal price, Integer stock, Instant updatedAt) {
        return new Product(id, name, price, stock, updatedAt);
    }

}