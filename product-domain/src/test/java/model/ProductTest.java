package model;

import com.product.domain.product.exception.ProductErrorCode;
import com.product.domain.product.exception.ProductException;
import com.product.domain.product.model.Product;
import com.product.domain.product.model.ProductDisplayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    @DisplayName("Product 생성 시 전달한 값이 유지된다")
    void constructor_shouldAssignFields() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        Product product = new Product(
                1L,
                "상품A",
                new BigDecimal("10000"),
                10,
                updatedAt
        );

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("상품A");
        assertThat(product.getPrice()).isEqualByComparingTo("10000");
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(product.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("of 팩토리 메서드로 Product 를 생성할 수 있다")
    void of_shouldCreateProduct() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        Product product = Product.of(
                1L,
                "상품A",
                new BigDecimal("10000"),
                10,
                updatedAt
        );

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("상품A");
        assertThat(product.getPrice()).isEqualByComparingTo("10000");
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(product.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("stock 이 null 이어도 Product 를 생성할 수 있다")
    void constructor_shouldAllowNullStock() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        Product product = new Product(
                1L,
                "상품A",
                new BigDecimal("10000"),
                null,
                updatedAt
        );

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("상품A");
        assertThat(product.getPrice()).isEqualByComparingTo("10000");
        assertThat(product.getStock()).isNull();
        assertThat(product.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("id 가 null 이면 ProductException 이 발생한다")
    void constructor_shouldThrowException_whenIdIsNull() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        assertThatThrownBy(() -> new Product(
                null,
                "상품A",
                new BigDecimal("10000"),
                10,
                updatedAt
        ))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_ID);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_ID.getMessage());
                });
    }

    @Test
    @DisplayName("id 가 0 이하면 ProductException 이 발생한다")
    void constructor_shouldThrowException_whenIdIsInvalid() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        assertThatThrownBy(() -> new Product(
                0L,
                "상품A",
                new BigDecimal("10000"),
                10,
                updatedAt
        ))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_ID);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_ID.getMessage());
                });
    }

    @Test
    @DisplayName("name 이 null 이면 ProductException 이 발생한다")
    void constructor_shouldThrowException_whenNameIsNull() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        assertThatThrownBy(() -> new Product(
                1L,
                null,
                new BigDecimal("10000"),
                10,
                updatedAt
        ))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_NAME);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_NAME.getMessage());
                });
    }

    @Test
    @DisplayName("name 이 blank 이면 ProductException 이 발생한다")
    void constructor_shouldThrowException_whenNameIsBlank() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        assertThatThrownBy(() -> new Product(
                1L,
                " ",
                new BigDecimal("10000"),
                10,
                updatedAt
        ))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_NAME);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_NAME.getMessage());
                });
    }

    @Test
    @DisplayName("price 가 null 이면 ProductException 이 발생한다")
    void constructor_shouldThrowException_whenPriceIsNull() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        assertThatThrownBy(() -> new Product(
                1L,
                "상품A",
                null,
                10,
                updatedAt
        ))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_PRICE);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_PRICE.getMessage());
                });
    }

    @Test
    @DisplayName("price 가 음수이면 ProductException 이 발생한다")
    void constructor_shouldThrowException_whenPriceIsNegative() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        assertThatThrownBy(() -> new Product(
                1L,
                "상품A",
                new BigDecimal("-1"),
                10,
                updatedAt
        ))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_PRICE);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_PRICE.getMessage());
                });
    }

    @Test
    @DisplayName("stock 이 음수이면 ProductException 이 발생한다")
    void constructor_shouldThrowException_whenStockIsNegative() {
        Instant updatedAt = Instant.parse("2026-03-20T00:00:00Z");

        assertThatThrownBy(() -> new Product(
                1L,
                "상품A",
                new BigDecimal("10000"),
                -1,
                updatedAt
        ))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_STOCK);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_STOCK.getMessage());
                });
    }

    @Test
    @DisplayName("updatedAt 이 null 이면 ProductException 이 발생한다")
    void constructor_shouldThrowException_whenUpdatedAtIsNull() {
        assertThatThrownBy(() -> new Product(
                1L,
                "상품A",
                new BigDecimal("10000"),
                10,
                null
        ))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_UPDATED_AT);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_UPDATED_AT.getMessage());
                });
    }

    @Test
    @DisplayName("availability 는 stock 기반으로 상태를 반환한다")
    void availability_shouldReturnProductAvailability() {
        Product onSaleProduct = new Product(
                1L,
                "상품A",
                new BigDecimal("10000"),
                10,
                Instant.parse("2026-03-20T00:00:00Z")
        );

        Product soldOutProduct = new Product(
                2L,
                "상품B",
                new BigDecimal("20000"),
                0,
                Instant.parse("2026-03-20T00:00:01Z")
        );

        Product unknownStockProduct = new Product(
                3L,
                "상품C",
                new BigDecimal("30000"),
                null,
                Instant.parse("2026-03-20T00:00:02Z")
        );

        assertThat(onSaleProduct.availability().displayStatus()).isEqualTo(ProductDisplayStatus.ON_SALE);
        assertThat(soldOutProduct.availability().displayStatus()).isEqualTo(ProductDisplayStatus.SOLD_OUT);
        assertThat(unknownStockProduct.availability().displayStatus()).isEqualTo(ProductDisplayStatus.UNKNOWN);
    }
}