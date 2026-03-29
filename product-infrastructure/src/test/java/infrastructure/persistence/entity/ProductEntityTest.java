package infrastructure.persistence.entity;

import com.product.infrastructure.persistence.entity.ProductEntity;
import com.product.infrastructure.persistence.repository.ProductJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ProductEntityTest {

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ProductEntity.class)
    @EnableJpaRepositories(basePackageClasses = ProductJpaRepository.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("ProductEntity 저장 후 다시 조회하면 값이 유지된다")
    void saveAndLoad_success() {
        ProductEntity entity = ProductEntity.builder()
                .id(100L)
                .name("엔티티테스트")
                .price(new BigDecimal("12345.67"))
                .stock(42)
                .updatedAt(Instant.parse("2026-03-20T00:00:00Z"))
                .build();

        productJpaRepository.saveAndFlush(entity);
        entityManager.clear();

        Optional<ProductEntity> actual = productJpaRepository.findById(100L);

        assertThat(actual).isPresent();
        assertThat(actual.get().getId()).isEqualTo(100L);
        assertThat(actual.get().getName()).isEqualTo("엔티티테스트");
        assertThat(actual.get().getPrice()).isEqualByComparingTo("12345.67");
        assertThat(actual.get().getStock()).isEqualTo(42);
        assertThat(actual.get().getUpdatedAt()).isEqualTo(Instant.parse("2026-03-20T00:00:00Z"));
    }

    @Test
    @DisplayName("price 가 음수이면 저장에 실패한다")
    void save_withNegativePrice_thenFail() {
        ProductEntity entity = ProductEntity.builder()
                .id(101L)
                .name("잘못된가격상품")
                .price(new BigDecimal("-1.00"))
                .stock(10)
                .updatedAt(Instant.parse("2026-03-20T00:00:00Z"))
                .build();

        assertThatThrownBy(() -> productJpaRepository.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("stock 이 음수이면 저장에 실패한다")
    void save_withNegativeStock_thenFail() {
        ProductEntity entity = ProductEntity.builder()
                .id(102L)
                .name("잘못된재고상품")
                .price(new BigDecimal("1000.00"))
                .stock(-1)
                .updatedAt(Instant.parse("2026-03-20T00:00:00Z"))
                .build();

        assertThatThrownBy(() -> productJpaRepository.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}