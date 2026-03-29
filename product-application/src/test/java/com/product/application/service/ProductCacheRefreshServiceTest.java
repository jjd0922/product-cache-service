package com.product.application.service;

import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.application.dto.result.ProductResult;
import com.product.application.factory.ProductResultFactory;
import com.product.application.factory.ProductRuntimeCacheFactory;
import com.product.application.port.out.ProductDetailCachePort;
import com.product.application.port.out.ProductRuntimeCachePort;
import com.product.domain.product.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheRefreshServiceTest {

    @Mock
    private ProductDetailCachePort productDetailCachePort;

    @Mock
    private ProductRuntimeCachePort productRuntimeCachePort;

    @Mock
    private ProductResultFactory productResultFactory;

    @Mock
    private ProductRuntimeCacheFactory productRuntimeCacheFactory;

    @InjectMocks
    private ProductCacheRefreshService productCacheRefreshService;

    @Test
    @DisplayName("refresh 는 유효한 상품이면 detail/runtime cache 를 각각 저장한다")
    void refresh_whenProductIsValid_thenPutDetailAndRuntime() {
        Product product = product(1L, "상품A", new BigDecimal("10000"), 10);
        ProductResult detail = mock(ProductResult.class);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);

        when(productResultFactory.from(product)).thenReturn(detail);
        when(productRuntimeCacheFactory.from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        )).thenReturn(runtime);

        productCacheRefreshService.refresh(product);

        verify(productResultFactory).from(product);
        verify(productRuntimeCacheFactory).from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        );
        verify(productDetailCachePort).put(detail);
        verify(productRuntimeCachePort).put(runtime);
    }

    @Test
    @DisplayName("refresh 는 상품이 null 이면 아무 작업도 하지 않는다")
    void refresh_whenProductIsNull_thenDoNothing() {
        productCacheRefreshService.refresh(null);

        verifyNoInteractions(
                productDetailCachePort,
                productRuntimeCachePort,
                productResultFactory,
                productRuntimeCacheFactory
        );
    }

    @Test
    @DisplayName("refresh 는 상품 ID 가 유효하지 않으면 아무 작업도 하지 않는다")
    void refresh_whenProductIdIsInvalid_thenDoNothing() {
        Product nullIdProduct = mock(Product.class);
        Product zeroIdProduct = mock(Product.class);
        Product negativeIdProduct = mock(Product.class);

        when(nullIdProduct.getId()).thenReturn(null);
        when(zeroIdProduct.getId()).thenReturn(0L);
        when(negativeIdProduct.getId()).thenReturn(-1L);

        productCacheRefreshService.refresh(nullIdProduct);
        productCacheRefreshService.refresh(zeroIdProduct);
        productCacheRefreshService.refresh(negativeIdProduct);

        verifyNoInteractions(
                productDetailCachePort,
                productRuntimeCachePort,
                productResultFactory,
                productRuntimeCacheFactory
        );
    }

    @Test
    @DisplayName("refresh 는 detail 이 null 이면 runtime 만 저장한다")
    void refresh_whenDetailIsNull_thenPutOnlyRuntime() {
        Product product = product(1L, "상품A", new BigDecimal("10000"), 10);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);

        when(productResultFactory.from(product)).thenReturn(null);
        when(productRuntimeCacheFactory.from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        )).thenReturn(runtime);

        productCacheRefreshService.refresh(product);

        verify(productDetailCachePort, never()).put(any());
        verify(productRuntimeCachePort).put(runtime);
    }

    @Test
    @DisplayName("refresh 는 runtime 이 null 이면 detail 만 저장한다")
    void refresh_whenRuntimeIsNull_thenPutOnlyDetail() {
        Product product = product(1L, "상품A", new BigDecimal("10000"), 10);
        ProductResult detail = mock(ProductResult.class);

        when(productResultFactory.from(product)).thenReturn(detail);
        when(productRuntimeCacheFactory.from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        )).thenReturn(null);

        productCacheRefreshService.refresh(product);

        verify(productDetailCachePort).put(detail);
        verify(productRuntimeCachePort, never()).put(any());
    }

    @Test
    @DisplayName("refreshAll 은 유효한 상품만 batch 저장한다")
    void refreshAll_whenProductsContainInvalid_thenPutOnlyValidResults() {
        Product valid1 = product(1L, "상품A", new BigDecimal("10000"), 10);
        Product valid2 = product(2L, "상품B", new BigDecimal("20000"), 20);

        Product invalid = mock(Product.class);
        when(invalid.getId()).thenReturn(0L);

        ProductResult detail1 = mock(ProductResult.class);
        ProductResult detail2 = mock(ProductResult.class);
        ProductRuntimeCacheData runtime1 = mock(ProductRuntimeCacheData.class);
        ProductRuntimeCacheData runtime2 = mock(ProductRuntimeCacheData.class);

        when(productResultFactory.from(valid1)).thenReturn(detail1);
        when(productResultFactory.from(valid2)).thenReturn(detail2);

        when(productRuntimeCacheFactory.from(valid1.getId(), null, valid1.getStock(), valid1.getUpdatedAt()))
                .thenReturn(runtime1);
        when(productRuntimeCacheFactory.from(valid2.getId(), null, valid2.getStock(), valid2.getUpdatedAt()))
                .thenReturn(runtime2);

        productCacheRefreshService.refreshAll(java.util.Arrays.asList(valid1, null, invalid, valid2));

        ArgumentCaptor<Collection<ProductResult>> detailCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<ProductRuntimeCacheData>> runtimeCaptor = ArgumentCaptor.forClass(Collection.class);

        verify(productDetailCachePort).putAll(detailCaptor.capture());
        verify(productRuntimeCachePort).putAll(runtimeCaptor.capture());

        assertThat(detailCaptor.getValue()).containsExactly(detail1, detail2);
        assertThat(runtimeCaptor.getValue()).containsExactly(runtime1, runtime2);
    }

    @Test
    @DisplayName("refreshAll 은 입력이 null 이면 아무 작업도 하지 않는다")
    void refreshAll_whenProductsIsNull_thenDoNothing() {
        productCacheRefreshService.refreshAll(null);

        verifyNoInteractions(
                productDetailCachePort,
                productRuntimeCachePort,
                productResultFactory,
                productRuntimeCacheFactory
        );
    }

    @Test
    @DisplayName("refreshAll 은 입력이 비어 있으면 아무 작업도 하지 않는다")
    void refreshAll_whenProductsIsEmpty_thenDoNothing() {
        productCacheRefreshService.refreshAll(List.of());

        verifyNoInteractions(
                productDetailCachePort,
                productRuntimeCachePort,
                productResultFactory,
                productRuntimeCacheFactory
        );
    }

    @Test
    @DisplayName("refreshAll 은 detail 결과만 있으면 detail cache 만 batch 저장한다")
    void refreshAll_whenOnlyDetailExists_thenPutOnlyDetailBatch() {
        Product product = product(1L, "상품A", new BigDecimal("10000"), 10);
        ProductResult detail = mock(ProductResult.class);

        when(productResultFactory.from(product)).thenReturn(detail);
        when(productRuntimeCacheFactory.from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        )).thenReturn(null);

        productCacheRefreshService.refreshAll(List.of(product));

        verify(productDetailCachePort).putAll(argThat(results ->
                results.size() == 1 && results.contains(detail)
        ));
        verify(productRuntimeCachePort, never()).putAll(any());
    }

    @Test
    @DisplayName("refreshAll 은 runtime 결과만 있으면 runtime cache 만 batch 저장한다")
    void refreshAll_whenOnlyRuntimeExists_thenPutOnlyRuntimeBatch() {
        Product product = product(1L, "상품A", new BigDecimal("10000"), 10);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);

        when(productResultFactory.from(product)).thenReturn(null);
        when(productRuntimeCacheFactory.from(
                product.getId(),
                null,
                product.getStock(),
                product.getUpdatedAt()
        )).thenReturn(runtime);

        productCacheRefreshService.refreshAll(List.of(product));

        verify(productDetailCachePort, never()).putAll(any());
        verify(productRuntimeCachePort).putAll(argThat(results ->
                results.size() == 1 && results.contains(runtime)
        ));
    }

    @Test
    @DisplayName("evict 는 유효한 상품 ID 이면 detail/runtime cache 를 모두 삭제한다")
    void evict_whenProductIdIsValid_thenEvictBothCaches() {
        productCacheRefreshService.evict(1L);

        verify(productDetailCachePort).evict(1L);
        verify(productRuntimeCachePort).evict(1L);
    }

    @Test
    @DisplayName("evict 는 유효하지 않은 상품 ID 이면 아무 작업도 하지 않는다")
    void evict_whenProductIdIsInvalid_thenDoNothing() {
        productCacheRefreshService.evict(null);
        productCacheRefreshService.evict(0L);
        productCacheRefreshService.evict(-1L);

        verifyNoInteractions(productDetailCachePort, productRuntimeCachePort);
    }

    @Test
    @DisplayName("evictAll 은 유효한 ID 만 필터링해서 삭제한다")
    void evictAll_whenContainsInvalidIds_thenEvictOnlyValidIds() {
        productCacheRefreshService.evictAll(java.util.Arrays.asList(null, -1L, 0L, 1L, 2L));

        verify(productDetailCachePort).evictAll(List.of(1L, 2L));
        verify(productRuntimeCachePort).evictAll(List.of(1L, 2L));
    }

    @Test
    @DisplayName("evictAll 은 중복 ID 를 제거하지 않고 그대로 전달한다")
    void evictAll_whenDuplicateIdsExist_thenKeepDuplicates() {
        productCacheRefreshService.evictAll(List.of(1L, 2L, 1L));

        verify(productDetailCachePort).evictAll(List.of(1L, 2L, 1L));
        verify(productRuntimeCachePort).evictAll(List.of(1L, 2L, 1L));
    }

    @Test
    @DisplayName("evictAll 은 유효한 ID 가 없으면 아무 작업도 하지 않는다")
    void evictAll_whenNoValidIds_thenDoNothing() {
        productCacheRefreshService.evictAll(java.util.Arrays.asList(null, -1L, 0L));

        verifyNoInteractions(productDetailCachePort, productRuntimeCachePort);
    }

    @Test
    @DisplayName("evictAll 은 입력이 null 이면 아무 작업도 하지 않는다")
    void evictAll_whenIdsAreNull_thenDoNothing() {
        productCacheRefreshService.evictAll(null);

        verifyNoInteractions(productDetailCachePort, productRuntimeCachePort);
    }

    private Product product(Long id, String name, BigDecimal price, Integer stock) {
        return new Product(
                id,
                name,
                price,
                stock,
                Instant.parse("2026-03-20T00:00:00Z")
        );
    }
}