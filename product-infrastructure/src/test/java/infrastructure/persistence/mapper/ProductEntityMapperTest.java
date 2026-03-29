package infrastructure.persistence.mapper;

import com.product.domain.product.model.Product;
import com.product.infrastructure.persistence.entity.ProductEntity;
import com.product.infrastructure.persistence.mapper.ProductEntityMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProductEntityMapperTest {

    private final ProductEntityMapper productEntityMapper = new ProductEntityMapper();

    @Test
    @DisplayName("ProductEntity 를 Product 로 변환한다")
    void toDomain() {
        ProductEntity entity = ProductEntity.builder()
                .id(1L)
                .name("상품A")
                .price(new BigDecimal("10000.00"))
                .stock(10)
                .updatedAt(Instant.parse("2026-03-20T00:00:00Z"))
                .build();

        Product actual = productEntityMapper.toDomain(entity);

        assertThat(actual.getId()).isEqualTo(1L);
        assertThat(actual.getName()).isEqualTo("상품A");
        assertThat(actual.getPrice()).isEqualByComparingTo("10000.00");
        assertThat(actual.getStock()).isEqualTo(10);
        assertThat(actual.getUpdatedAt()).isEqualTo(Instant.parse("2026-03-20T00:00:00Z"));
    }
}