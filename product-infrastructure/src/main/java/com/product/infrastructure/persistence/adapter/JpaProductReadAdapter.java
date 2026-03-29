package com.product.infrastructure.persistence.adapter;

import com.product.application.port.out.ProductReadPort;
import com.product.domain.product.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaProductReadAdapter implements ProductReadPort {

    @Override
    public Optional<Product> findById(Long productId) {
        return Optional.empty();
    }

    @Override
    public List<Product> findAllByIdIn(Collection<Long> productIds) {
        return List.of();
    }

    @Override
    public List<Product> findAll() {
        return List.of();
    }

    @Override
    public List<Long> findAllIds() {
        return List.of();
    }
}
