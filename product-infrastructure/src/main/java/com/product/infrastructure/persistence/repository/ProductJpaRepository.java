package com.product.infrastructure.persistence.repository;

import com.product.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    List<ProductEntity> findByIdIn(Collection<Long> ids);

    Page<ProductEntity> findByUpdatedAtAfter(Instant updatedSince, Pageable pageable);

    Page<ProductEntity> findByIdBetween(Long from, Long to, Pageable pageable);

    Page<ProductEntity> findByUpdatedAtAfterAndIdBetween(Instant updatedSince, Long from, Long to, Pageable pageable);

    long countByUpdatedAtAfter(Instant updatedSince);

    long countByIdBetween(Long from, Long to);

    long countByUpdatedAtAfterAndIdBetween(Instant updatedSince, Long from, Long to);

    @Query("select p.id from ProductEntity p order by p.id asc")
    List<Long> findAllIds();

    @Query("select p.id from ProductEntity p where p.id > :lastProductId order by p.id asc")
    List<Long> findIdsAfter(@Param("lastProductId") Long lastProductId, Pageable pageable);
}
