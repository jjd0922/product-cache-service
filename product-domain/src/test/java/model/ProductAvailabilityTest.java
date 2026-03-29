package model;

import com.product.domain.product.exception.ProductErrorCode;
import com.product.domain.product.exception.ProductException;
import com.product.domain.product.model.ProductAvailability;
import com.product.domain.product.model.ProductDisplayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAvailabilityTest {

    @Test
    @DisplayName("재고가 0보다 크면 soldOut 은 false 이다")
    void from_whenStockIsPositive_thenNotSoldOut() {
        ProductAvailability availability = ProductAvailability.from(10);

        assertThat(availability.isSoldOut()).isFalse();
        assertThat(availability.displayStatus()).isEqualTo(ProductDisplayStatus.ON_SALE);
    }

    @Test
    @DisplayName("재고가 0이면 soldOut 은 true 이다")
    void from_whenStockIsZero_thenSoldOut() {
        ProductAvailability availability = ProductAvailability.from(0);

        assertThat(availability.isSoldOut()).isTrue();
        assertThat(availability.displayStatus()).isEqualTo(ProductDisplayStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("재고가 null 이면 displayStatus 는 UNKNOWN 이다")
    void from_whenStockIsNull_thenUnknown() {
        ProductAvailability availability = ProductAvailability.from(null);

        assertThat(availability.isSoldOut()).isFalse();
        assertThat(availability.displayStatus()).isEqualTo(ProductDisplayStatus.UNKNOWN);
    }

    @Test
    @DisplayName("재고가 음수이면 ProductException 이 발생한다")
    void from_whenStockIsNegative_thenThrowException() {
        assertThatThrownBy(() -> ProductAvailability.from(-1))
                .isInstanceOfSatisfying(ProductException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_STOCK);
                    assertThat(ex.getMessage()).isEqualTo(ProductErrorCode.INVALID_PRODUCT_STOCK.getMessage());
                });
    }
}