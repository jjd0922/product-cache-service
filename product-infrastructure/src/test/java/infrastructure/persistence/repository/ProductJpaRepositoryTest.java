package infrastructure.persistence.repository;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductJpaRepositoryTest {

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
    @DisplayName("findByIdIn 은 전달한 ID 목록에 해당하는 상품만 조회한다")
    void findByIdIn_returnsMatchingEntities() {
        persist(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
        persist(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");
        persist(3L, "상품C", "30000.00", 0, "2026-03-20T00:00:02Z");

        entityManager.flush();
        entityManager.clear();

        List<ProductEntity> actual = productJpaRepository.findByIdIn(List.of(1L, 3L));

        assertThat(actual).hasSize(2);
        assertThat(actual).extracting(ProductEntity::getId)
                .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("findByUpdatedAtAfter 는 기준 시각 이후 데이터만 조회한다")
    void findByUpdatedAtAfter_returnsEntitiesAfterBoundary() {
        Instant boundary = Instant.parse("2026-03-20T00:00:01Z");

        persist(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
        persist(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");
        persist(3L, "상품C", "30000.00", 0, "2026-03-20T00:00:02Z");
        persist(4L, "상품D", "40000.00", 7, "2026-03-20T00:00:03Z");

        entityManager.flush();
        entityManager.clear();

        Page<ProductEntity> actual = productJpaRepository.findByUpdatedAtAfter(
                boundary,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(actual.getContent()).extracting(ProductEntity::getId)
                .containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("findByIdBetween 은 시작과 끝 경계를 포함한다")
    void findByIdBetween_includesBoundaries() {
        persist(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
        persist(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");
        persist(3L, "상품C", "30000.00", 0, "2026-03-20T00:00:02Z");
        persist(4L, "상품D", "40000.00", 7, "2026-03-20T00:00:03Z");

        entityManager.flush();
        entityManager.clear();

        Page<ProductEntity> actual = productJpaRepository.findByIdBetween(
                2L,
                3L,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(actual.getContent()).extracting(ProductEntity::getId)
                .containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("findByUpdatedAtAfterAndIdBetween 은 두 조건을 모두 만족하는 데이터만 조회한다")
    void findByUpdatedAtAfterAndIdBetween_returnsMatchingEntities() {
        Instant boundary = Instant.parse("2026-03-20T00:00:01Z");

        persist(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
        persist(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");
        persist(3L, "상품C", "30000.00", 0, "2026-03-20T00:00:02Z");
        persist(4L, "상품D", "40000.00", 7, "2026-03-20T00:00:03Z");
        persist(5L, "상품E", "50000.00", 9, "2026-03-20T00:00:04Z");

        entityManager.flush();
        entityManager.clear();

        Page<ProductEntity> actual = productJpaRepository.findByUpdatedAtAfterAndIdBetween(
                boundary,
                2L,
                4L,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(actual.getContent()).extracting(ProductEntity::getId)
                .containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("findByIdIn 에 빈 컬렉션을 전달하면 빈 목록을 반환한다")
    void findByIdIn_whenEmptyIds_thenReturnEmptyList() {
        List<ProductEntity> actual = productJpaRepository.findByIdIn(List.of());

        assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("countByUpdatedAtAfter 는 기준 시각 이후 데이터 개수를 반환한다")
    void countByUpdatedAtAfter_returnsCorrectCount() {
        Instant boundary = Instant.parse("2026-03-20T00:00:01Z");

        persist(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
        persist(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");
        persist(3L, "상품C", "30000.00", 0, "2026-03-20T00:00:02Z");
        persist(4L, "상품D", "40000.00", 7, "2026-03-20T00:00:03Z");

        entityManager.flush();
        entityManager.clear();

        long actual = productJpaRepository.countByUpdatedAtAfter(boundary);

        assertThat(actual).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByIdBetween 는 시작과 끝 경계를 포함한 개수를 반환한다")
    void countByIdBetween_returnsCorrectCount() {
        persist(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
        persist(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");
        persist(3L, "상품C", "30000.00", 0, "2026-03-20T00:00:02Z");
        persist(4L, "상품D", "40000.00", 7, "2026-03-20T00:00:03Z");

        entityManager.flush();
        entityManager.clear();

        long actual = productJpaRepository.countByIdBetween(2L, 3L);

        assertThat(actual).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByUpdatedAtAfterAndIdBetween 는 두 조건을 모두 만족하는 개수를 반환한다")
    void countByUpdatedAtAfterAndIdBetween_returnsCorrectCount() {
        Instant boundary = Instant.parse("2026-03-20T00:00:01Z");

        persist(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
        persist(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");
        persist(3L, "상품C", "30000.00", 0, "2026-03-20T00:00:02Z");
        persist(4L, "상품D", "40000.00", 7, "2026-03-20T00:00:03Z");
        persist(5L, "상품E", "50000.00", 9, "2026-03-20T00:00:04Z");

        entityManager.flush();
        entityManager.clear();

        long actual = productJpaRepository.countByUpdatedAtAfterAndIdBetween(boundary, 2L, 4L);

        assertThat(actual).isEqualTo(2L);
    }

    @Test
    @DisplayName("findAllIds 는 전체 상품 ID 를 오름차순으로 조회한다")
    void findIdsAfter_returnsIdsAfterCursorInAscendingOrder() {
        persist(3L, "상품C", "30000.00", 0, "2026-03-20T00:00:02Z");
        persist(1L, "상품A", "10000.00", 10, "2026-03-20T00:00:00Z");
        persist(2L, "상품B", "20000.00", 5, "2026-03-20T00:00:01Z");

        entityManager.flush();
        entityManager.clear();

        List<Long> actual = productJpaRepository.findIdsAfter(1L, PageRequest.of(0, 2));

        assertThat(actual).containsExactly(2L, 3L);
    }

    private void persist(Long id, String name, String price, Integer stock, String updatedAt) {
        entityManager.persist(
                ProductEntity.builder()
                        .id(id)
                        .name(name)
                        .price(new BigDecimal(price))
                        .stock(stock)
                        .updatedAt(Instant.parse(updatedAt))
                        .build()
        );
    }
}
