package infrastructure.persistence.adapter;

import com.product.domain.product.model.Product;
import com.product.infrastructure.persistence.adapter.JpaProductReadAdapter;
import com.product.infrastructure.persistence.entity.ProductEntity;
import com.product.infrastructure.persistence.mapper.ProductEntityMapper;
import com.product.infrastructure.persistence.repository.ProductJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("상품이 존재하면 도메인 객체를 반환한다")
        void returnsProduct_whenEntityExists() {
            ProductEntity entity = productEntity(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
            Product product = product(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");

            when(productJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(productEntityMapper.toDomain(entity)).thenReturn(product);

            Optional<Product> actual = jpaProductReadAdapter.findById(1L);

            assertThat(actual).contains(product);
            verify(productJpaRepository).findById(1L);
            verify(productEntityMapper).toDomain(entity);
        }

        @Test
        @DisplayName("상품이 없으면 empty 를 반환한다")
        void returnsEmpty_whenEntityDoesNotExist() {
            when(productJpaRepository.findById(1L)).thenReturn(Optional.empty());

            Optional<Product> actual = jpaProductReadAdapter.findById(1L);

            assertThat(actual).isEmpty();
            verify(productJpaRepository).findById(1L);
            verifyNoInteractions(productEntityMapper);
        }

        @Test
        @DisplayName("상품 ID 가 null 이면 empty 를 반환한다")
        void returnsEmpty_whenProductIdIsNull() {
            Optional<Product> actual = jpaProductReadAdapter.findById(null);

            assertThat(actual).isEmpty();
            verifyNoInteractions(productJpaRepository, productEntityMapper);
        }

        @Test
        @DisplayName("상품 ID 가 0 이하이면 empty 를 반환한다")
        void returnsEmpty_whenProductIdIsInvalid() {
            Optional<Product> actual = jpaProductReadAdapter.findById(0L);

            assertThat(actual).isEmpty();
            verifyNoInteractions(productJpaRepository, productEntityMapper);
        }
    }

    @Nested
    @DisplayName("findAllByIdIn")
    class FindAllByIdIn {

        @Test
        @DisplayName("여러 상품을 조회하면 도메인 목록으로 변환해서 반환한다")
        void returnsMappedProducts() {
            ProductEntity entity1 = productEntity(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
            ProductEntity entity2 = productEntity(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");

            Product product1 = product(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
            Product product2 = product(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");

            when(productJpaRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(entity1, entity2));
            when(productEntityMapper.toDomain(entity1)).thenReturn(product1);
            when(productEntityMapper.toDomain(entity2)).thenReturn(product2);

            List<Product> actual = jpaProductReadAdapter.findAllByIdIn(List.of(1L, 2L));

            assertThat(actual).containsExactly(product1, product2);
            verify(productJpaRepository).findAllById(List.of(1L, 2L));
            verify(productEntityMapper).toDomain(entity1);
            verify(productEntityMapper).toDomain(entity2);
        }

        @Test
        @DisplayName("입력이 null 이면 빈 목록을 반환한다")
        void returnsEmptyList_whenIdsAreNull() {
            List<Product> actual = jpaProductReadAdapter.findAllByIdIn(null);

            assertThat(actual).isEmpty();
            verifyNoInteractions(productJpaRepository, productEntityMapper);
        }

        @Test
        @DisplayName("입력이 비어 있으면 빈 목록을 반환한다")
        void returnsEmptyList_whenIdsAreEmpty() {
            List<Product> actual = jpaProductReadAdapter.findAllByIdIn(List.of());

            assertThat(actual).isEmpty();
            verifyNoInteractions(productJpaRepository, productEntityMapper);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("전체 상품을 ID 오름차순으로 조회하여 도메인 목록으로 변환한다")
        void returnsAllMappedProducts() {
            ProductEntity entity1 = productEntity(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
            ProductEntity entity2 = productEntity(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");

            Product product1 = product(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
            Product product2 = product(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");

            when(productJpaRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of(entity1, entity2));
            when(productEntityMapper.toDomain(entity1)).thenReturn(product1);
            when(productEntityMapper.toDomain(entity2)).thenReturn(product2);

            List<Product> actual = jpaProductReadAdapter.findAll();

            assertThat(actual).containsExactly(product1, product2);
            verify(productJpaRepository).findAll(Sort.by(Sort.Direction.ASC, "id"));
            verify(productEntityMapper).toDomain(entity1);
            verify(productEntityMapper).toDomain(entity2);
        }
    }

    @Test
    @DisplayName("findAllByIdIn 에서 repository 결과가 비어 있으면 빈 목록을 반환하고 mapper 는 호출되지 않는다")
    void returnsEmptyList_whenRepositoryReturnsEmptyList() {
        when(productJpaRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of());

        List<Product> actual = jpaProductReadAdapter.findAllByIdIn(List.of(1L, 2L));

        assertThat(actual).isEmpty();
        verify(productJpaRepository).findAllById(List.of(1L, 2L));
        verifyNoInteractions(productEntityMapper);
    }

    @Test
    @DisplayName("findAll 에서 repository 결과가 비어 있으면 빈 목록을 반환한다")
    void returnsEmptyList_whenFindAllReturnsEmpty() {
        when(productJpaRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of());

        List<Product> actual = jpaProductReadAdapter.findAll();

        assertThat(actual).isEmpty();
        verify(productJpaRepository).findAll(Sort.by(Sort.Direction.ASC, "id"));
        verifyNoInteractions(productEntityMapper);
    }

    @Test
    @DisplayName("전체 상품 ID 목록을 조회한다")
    void returnsAllIds() {
        when(productJpaRepository.findAllIds()).thenReturn(List.of(1L, 2L, 3L));

        List<Long> actual = jpaProductReadAdapter.findAllIds();

        assertThat(actual).containsExactly(1L, 2L, 3L);
        verify(productJpaRepository).findAllIds();
        verifyNoInteractions(productEntityMapper);
    }

    @Test
    @DisplayName("countAll returns product count")
    void returnsAllCount() {
        when(productJpaRepository.count()).thenReturn(3L);

        long actual = jpaProductReadAdapter.countAll();

        assertThat(actual).isEqualTo(3L);
        verify(productJpaRepository).count();
        verifyNoInteractions(productEntityMapper);
    }

    @Test
    @DisplayName("findIdsAfter returns ids after cursor")
    void returnsIdsAfterCursor() {
        when(productJpaRepository.findIdsAfter(10L, PageRequest.of(0, 3))).thenReturn(List.of(11L, 12L, 13L));

        List<Long> actual = jpaProductReadAdapter.findIdsAfter(10L, 3);

        assertThat(actual).containsExactly(11L, 12L, 13L);
        verify(productJpaRepository).findIdsAfter(10L, PageRequest.of(0, 3));
        verifyNoInteractions(productEntityMapper);
    }

    @Test
    @DisplayName("findIdsAfter returns empty when cursor or limit is invalid")
    void returnsEmptyList_whenFindIdsAfterInputIsInvalid() {
        assertThat(jpaProductReadAdapter.findIdsAfter(null, 3)).isEmpty();
        assertThat(jpaProductReadAdapter.findIdsAfter(-1L, 3)).isEmpty();
        assertThat(jpaProductReadAdapter.findIdsAfter(0L, 0)).isEmpty();

        verifyNoInteractions(productJpaRepository, productEntityMapper);
    }

    private ProductEntity productEntity(Long id, String name, String price, Integer stock, String updatedAt) {
        return ProductEntity.builder()
                .id(id)
                .name(name)
                .price(new BigDecimal(price))
                .stock(stock)
                .updatedAt(Instant.parse(updatedAt))
                .build();
    }

    private Product product(Long id, String name, String price, Integer stock, String updatedAt) {
        return new Product(
                id,
                name,
                new BigDecimal(price),
                stock,
                Instant.parse(updatedAt)
        );
    }


}
