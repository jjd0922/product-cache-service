package infrastructure.persistence.adapter;

import com.product.domain.product.model.Product;
import com.product.infrastructure.persistence.adapter.JpaProductReadAdapter;
import com.product.infrastructure.persistence.entity.ProductEntity;
import com.product.infrastructure.persistence.mapper.ProductEntityMapper;
import com.product.infrastructure.persistence.repository.ProductJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaProductReadAdapterTest {

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private ProductEntityMapper productEntityMapper;

    @InjectMocks
    private JpaProductReadAdapter jpaProductReadAdapter;

    @Test
    void findById_returnsProduct_whenEntityExists() {
        ProductEntity entity = productEntity(1L);
        Product product = product(1L);

        when(productJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(productEntityMapper.toDomain(entity)).thenReturn(product);

        Optional<Product> actual = jpaProductReadAdapter.findById(1L);

        assertThat(actual).contains(product);
        verify(productJpaRepository).findById(1L);
        verify(productEntityMapper).toDomain(entity);
    }

    @Test
    void findById_returnsEmpty_whenProductIdIsInvalid() {
        assertThat(jpaProductReadAdapter.findById(null)).isEmpty();
        assertThat(jpaProductReadAdapter.findById(0L)).isEmpty();

        verifyNoInteractions(productJpaRepository, productEntityMapper);
    }

    @Test
    void findAllByIdIn_returnsMappedProducts() {
        ProductEntity entity1 = productEntity(1L);
        ProductEntity entity2 = productEntity(2L);
        Product product1 = product(1L);
        Product product2 = product(2L);

        when(productJpaRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(entity1, entity2));
        when(productEntityMapper.toDomain(entity1)).thenReturn(product1);
        when(productEntityMapper.toDomain(entity2)).thenReturn(product2);

        List<Product> actual = jpaProductReadAdapter.findAllByIdIn(List.of(1L, 2L));

        assertThat(actual).containsExactly(product1, product2);
        verify(productJpaRepository).findAllById(List.of(1L, 2L));
    }

    @Test
    void findAllByIdIn_returnsEmpty_whenIdsAreEmpty() {
        assertThat(jpaProductReadAdapter.findAllByIdIn(null)).isEmpty();
        assertThat(jpaProductReadAdapter.findAllByIdIn(List.of())).isEmpty();

        verifyNoInteractions(productJpaRepository, productEntityMapper);
    }

    @Test
    void findAll_returnsMappedProducts() {
        ProductEntity entity = productEntity(1L);
        Product product = product(1L);

        when(productJpaRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of(entity));
        when(productEntityMapper.toDomain(entity)).thenReturn(product);

        List<Product> actual = jpaProductReadAdapter.findAll();

        assertThat(actual).containsExactly(product);
        verify(productJpaRepository).findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void countAll_returnsProductCount() {
        when(productJpaRepository.count()).thenReturn(3L);

        long actual = jpaProductReadAdapter.countAll();

        assertThat(actual).isEqualTo(3L);
        verify(productJpaRepository).count();
        verifyNoInteractions(productEntityMapper);
    }

    @Test
    void findIdsAfter_returnsIdsAfterCursor() {
        when(productJpaRepository.findIdsAfter(10L, PageRequest.of(0, 3))).thenReturn(List.of(11L, 12L, 13L));

        List<Long> actual = jpaProductReadAdapter.findIdsAfter(10L, 3);

        assertThat(actual).containsExactly(11L, 12L, 13L);
        verify(productJpaRepository).findIdsAfter(10L, PageRequest.of(0, 3));
        verifyNoInteractions(productEntityMapper);
    }

    @Test
    void findIdsAfter_returnsEmpty_whenInputIsInvalid() {
        assertThat(jpaProductReadAdapter.findIdsAfter(null, 3)).isEmpty();
        assertThat(jpaProductReadAdapter.findIdsAfter(-1L, 3)).isEmpty();
        assertThat(jpaProductReadAdapter.findIdsAfter(0L, 0)).isEmpty();

        verifyNoInteractions(productJpaRepository, productEntityMapper);
    }

    private ProductEntity productEntity(Long id) {
        return ProductEntity.builder()
                .id(id)
                .name("product-" + id)
                .price(new BigDecimal("10000.00"))
                .stock(10)
                .updatedAt(Instant.parse("2026-03-20T00:00:00Z"))
                .build();
    }

    private Product product(Long id) {
        return new Product(
                id,
                "product-" + id,
                new BigDecimal("10000.00"),
                10,
                Instant.parse("2026-03-20T00:00:00Z")
        );
    }
}
