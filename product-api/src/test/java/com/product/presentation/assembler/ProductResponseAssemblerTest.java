package com.product.presentation.assembler;

import com.product.application.dto.result.ProductResult;
import com.product.presentation.assembler.ProductResponseAssembler;
import com.product.presentation.dto.response.ProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProductResponseAssemblerTest {

    private final ProductResponseAssembler assembler = new ProductResponseAssembler();

    @Test
    @DisplayName("ProductResult 를 ProductResponse 로 변환한다")
    void from_shouldMapAllFields() {
        ProductResult source = new ProductResult(
                1L,
                "상품A",
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                10,
                false,
                "ON_SALE",
                Instant.parse("2026-03-20T00:00:00Z")
        );

        ProductResponse actual = assembler.from(source);

        assertThat(actual).isNotNull();
        assertThat(actual.id()).isEqualTo(1L);
        assertThat(actual.name()).isEqualTo("상품A");
        assertThat(actual.price()).isEqualByComparingTo("10000");
        assertThat(actual.salePrice()).isEqualByComparingTo("9000");
        assertThat(actual.stock()).isEqualTo(10);
        assertThat(actual.soldOut()).isFalse();
        assertThat(actual.displayStatus()).isEqualTo("ON_SALE");
        assertThat(actual.updatedAt()).isEqualTo(Instant.parse("2026-03-20T00:00:00Z"));
    }

    @Test
    @DisplayName("salePrice 가 null 이어도 그대로 변환한다")
    void from_shouldKeepNullSalePrice() {
        ProductResult source = new ProductResult(
                2L,
                "상품B",
                new BigDecimal("20000"),
                null,
                0,
                true,
                "SOLD_OUT",
                Instant.parse("2026-03-20T00:00:01Z")
        );

        ProductResponse actual = assembler.from(source);

        assertThat(actual).isNotNull();
        assertThat(actual.id()).isEqualTo(2L);
        assertThat(actual.name()).isEqualTo("상품B");
        assertThat(actual.price()).isEqualByComparingTo("20000");
        assertThat(actual.salePrice()).isNull();
        assertThat(actual.stock()).isEqualTo(0);
        assertThat(actual.soldOut()).isTrue();
        assertThat(actual.displayStatus()).isEqualTo("SOLD_OUT");
        assertThat(actual.updatedAt()).isEqualTo(Instant.parse("2026-03-20T00:00:01Z"));
    }
}