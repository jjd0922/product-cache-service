package com.product.infrastructure.persistence.adapter;

import com.product.application.port.out.ProductReadPort;
import com.product.domain.product.model.Product;
import com.product.infrastructure.persistence.mapper.ProductEntityMapper;
import com.product.infrastructure.persistence.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaProductReadAdapter implements ProductReadPort {

    private final ProductJpaRepository productJpaRepository;
    private final ProductEntityMapper productEntityMapper;

    @Override
    public Optional<Product> findById(Long productId) {
        if (productId == null || productId < 1) {
            return Optional.empty();
        }

        return productJpaRepository.findById(productId)
                .map(productEntityMapper::toDomain);
    }

    @Override
    public List<Product> findAllByIdIn(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        return productJpaRepository.findAllById(productIds).stream()
                .map(productEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(productEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<Long> findAllIds() {
        return productJpaRepository.findAllIds();
    }
}