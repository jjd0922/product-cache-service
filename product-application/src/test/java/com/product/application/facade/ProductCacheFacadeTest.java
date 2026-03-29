package com.product.application.facade;

import com.product.application.dto.result.ProductResult;
import com.product.application.service.ProductCacheQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheFacadeTest {

    @Mock
    private ProductCacheQueryService productCacheQueryService;

    @InjectMocks
    private ProductCacheFacade productCacheFacade;

    @Test
    @DisplayName("단건 조회는 ProductCacheQueryService 에 위임한다")
    void getProduct_shouldDelegateToQueryService() {
        ProductResult result = new ProductResult(
                1L,
                "상품A",
                new BigDecimal("10000"),
                null,
                10,
                false,
                "ON_SALE",
                Instant.parse("2026-03-20T00:00:00Z")
        );

        when(productCacheQueryService.getProduct(1L)).thenReturn(Optional.of(result));

        Optional<ProductResult> actual = productCacheFacade.getProduct(1L);

        assertThat(actual).contains(result);
        verify(productCacheQueryService).getProduct(1L);
    }

    @Test
    @DisplayName("다건 조회는 ProductCacheQueryService 에 위임한다")
    void getProducts_shouldDelegateToQueryService() {
        List<ProductResult> results = List.of(
                new ProductResult(1L, "상품A", new BigDecimal("10000"), null, 10, false, "ON_SALE", Instant.parse("2026-03-20T00:00:00Z")),
                new ProductResult(2L, "상품B", new BigDecimal("20000"), null, 0, true, "SOLD_OUT", Instant.parse("2026-03-20T00:00:01Z"))
        );

        when(productCacheQueryService.getProducts(List.of(1L, 2L))).thenReturn(results);

        List<ProductResult> actual = productCacheFacade.getProducts(List.of(1L, 2L));

        assertThat(actual).containsExactlyElementsOf(results);
        verify(productCacheQueryService).getProducts(List.of(1L, 2L));
    }
}
