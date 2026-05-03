package com.product.application.port.out;

import com.product.domain.product.model.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductReadPort {

    Optional<Product> findById(Long productId);

    List<Product> findAllByIdIn(Collection<Long> productIds);

    List<Product> findAll();

    long countAll();

    List<Long> findIdsAfter(Long lastProductId, int limit);
}
