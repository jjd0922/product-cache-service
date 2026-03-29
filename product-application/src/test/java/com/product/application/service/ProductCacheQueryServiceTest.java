package com.product.application.service;

import com.product.application.dto.result.ProductResult;
import com.product.application.factory.ProductResultFactory;
import com.product.application.port.out.ProductDetailCachePort;
import com.product.application.port.out.ProductReadPort;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheQueryServiceTest {

    private static final Instant FIXED_UPDATED_AT = Instant.parse("2026-03-29T00:00:00Z");

    @Mock
    private ProductReadPort productReadPort;

    @Mock
    private ProductDetailCachePort productDetailCachePort;

    @Mock
    private ProductResultFactory productResultFactory;

    @InjectMocks
    private ProductCacheQueryService productCacheQueryService;

    @Test
    @DisplayName("잘못된 상품 ID 이면 empty 를 반환한다")
    void getProduct_whenInvalidId_thenReturnEmpty() {
        assertThat(productCacheQueryService.getProduct(null)).isEmpty();
        assertThat(productCacheQueryService.getProduct(0L)).isEmpty();
        assertThat(productCacheQueryService.getProduct(-1L)).isEmpty();

        verifyNoInteractions(
                productReadPort,
                productDetailCachePort,
                productResultFactory
        );
    }

    @Test
    @DisplayName("detail cache hit 이면 캐시 값을 반환하고 DB fallback 하지 않는다")
    void getProduct_whenDetailCacheHit_thenReturnCachedValue() {
        Long productId = 1L;
        ProductResult cachedDetail = createProductResult(productId, "상품1");

        when(productDetailCachePort.getAll(List.of(productId)))
                .thenReturn(new LinkedHashMap<>(Map.of(productId, cachedDetail)));

        Optional<ProductResult> actual = productCacheQueryService.getProduct(productId);

        assertThat(actual).contains(cachedDetail);
        verify(productReadPort, never()).findAllByIdIn(anyCollection());
        verify(productDetailCachePort, never()).putAll(anyCollection());
        verifyNoInteractions(productResultFactory);
    }

    @Test
    @DisplayName("detail cache miss 이면 DB fallback 후 detail cache 를 재적재한다")
    void getProduct_whenDetailCacheMiss_thenFallbackToDbAndRepopulateDetailCache() {
        Long productId = 1L;

        Product product = mockProduct(productId);
        ProductResult fallbackDetail = createProductResult(productId, "상품1");

        when(productDetailCachePort.getAll(List.of(productId)))
                .thenReturn(new LinkedHashMap<>());
        when(productReadPort.findAllByIdIn(List.of(productId)))
                .thenReturn(List.of(product));
        when(productResultFactory.from(product))
                .thenReturn(fallbackDetail);

        Optional<ProductResult> actual = productCacheQueryService.getProduct(productId);

        assertThat(actual).contains(fallbackDetail);
        verify(productReadPort).findAllByIdIn(List.of(productId));
        verify(productResultFactory).from(product);
        verify(productDetailCachePort).putAll(argThat(details ->
                details.size() == 1 && details.contains(fallbackDetail)
        ));
    }

    @Test
    @DisplayName("다건 조회는 중복 제거 후 요청 순서를 유지한다")
    void getProducts_shouldDeduplicateAndKeepRequestOrder() {
        Long cachedProductId = 2L;
        Long fallbackProductId = 1L;
        Long notFoundProductId = 3L;

        ProductResult cached2 = createProductResult(cachedProductId, "상품2");
        Product product1 = mockProduct(fallbackProductId);
        ProductResult fallback1 = createProductResult(fallbackProductId, "상품1");

        when(productDetailCachePort.getAll(List.of(cachedProductId, fallbackProductId, notFoundProductId)))
                .thenReturn(new LinkedHashMap<>(Map.of(cachedProductId, cached2)));

        when(productReadPort.findAllByIdIn(List.of(fallbackProductId, notFoundProductId)))
                .thenReturn(List.of(product1));

        when(productResultFactory.from(product1)).thenReturn(fallback1);

        List<Long> requestIds = Arrays.asList(
                cachedProductId, fallbackProductId, cachedProductId, notFoundProductId, null, -1L, 0L
        );

        List<ProductResult> actual = productCacheQueryService.getProducts(requestIds);

        assertThat(actual).containsExactly(cached2, fallback1);

        ArgumentCaptor<Collection<ProductResult>> detailCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(productDetailCachePort).putAll(detailCaptor.capture());
        assertThat(detailCaptor.getValue()).containsExactly(fallback1);
    }

    @Test
    @DisplayName("DB fallback 후 detail cache 재적재 실패가 나도 결과는 반환한다")
    void getProducts_whenDetailCachePutFails_thenStillReturnResults() {
        Long productId = 1L;

        Product product = mockProduct(productId);
        ProductResult fallbackDetail = createProductResult(productId, "상품1");

        when(productDetailCachePort.getAll(List.of(productId)))
                .thenReturn(new LinkedHashMap<>());
        when(productReadPort.findAllByIdIn(List.of(productId)))
                .thenReturn(List.of(product));
        when(productResultFactory.from(product))
                .thenReturn(fallbackDetail);

        doThrow(new RuntimeException("detail put failed"))
                .when(productDetailCachePort).putAll(anyCollection());

        List<ProductResult> actual = productCacheQueryService.getProducts(List.of(productId));

        assertThat(actual).containsExactly(fallbackDetail);
        verify(productReadPort).findAllByIdIn(List.of(productId));
        verify(productDetailCachePort).putAll(argThat(details ->
                details.size() == 1 && details.contains(fallbackDetail)
        ));
    }

    @Test
    @DisplayName("DB fallback 결과가 비어 있으면 캐시 값만 반환한다")
    void getProducts_whenDbFallbackReturnsEmpty_thenReturnOnlyCachedResults() {
        Long cachedProductId = 2L;
        Long missingProductId = 1L;

        ProductResult cached2 = createProductResult(cachedProductId, "상품2");

        when(productDetailCachePort.getAll(List.of(cachedProductId, missingProductId)))
                .thenReturn(new LinkedHashMap<>(Map.of(cachedProductId, cached2)));
        when(productReadPort.findAllByIdIn(List.of(missingProductId)))
                .thenReturn(List.of());

        List<ProductResult> actual = productCacheQueryService.getProducts(List.of(cachedProductId, missingProductId));

        assertThat(actual).containsExactly(cached2);
        verify(productResultFactory, never()).from(any());
        verify(productDetailCachePort, never()).putAll(anyCollection());
    }

    @Test
    @DisplayName("DB 에서 조회된 상품 ID 가 null 이면 결과에서 제외한다")
    void getProducts_whenDbReturnedInvalidProduct_thenSkipInvalidProduct() {
        Long validId = 1L;
        Long invalidId = 999L;

        Product invalidProduct = mock(Product.class);
        Product validProduct = mockProduct(validId);

        ProductResult validResult = createProductResult(validId, "정상상품");

        when(invalidProduct.getId()).thenReturn(null);

        when(productDetailCachePort.getAll(List.of(validId, invalidId)))
                .thenReturn(new LinkedHashMap<>());
        when(productReadPort.findAllByIdIn(List.of(validId, invalidId)))
                .thenReturn(List.of(invalidProduct, validProduct));
        when(productResultFactory.from(validProduct)).thenReturn(validResult);

        List<ProductResult> actual = productCacheQueryService.getProducts(List.of(validId, invalidId));

        assertThat(actual).containsExactly(validResult);
        verify(productResultFactory, never()).from(invalidProduct);
        verify(productResultFactory).from(validProduct);
        verify(productDetailCachePort).putAll(argThat(details ->
                details.size() == 1 && details.contains(validResult)
        ));
    }

    @Test
    @DisplayName("factory 결과가 null 이면 결과에서 제외한다")
    void getProducts_whenFactoryReturnsNull_thenSkipResult() {
        Long productId = 1L;
        Product product = mockProduct(productId);

        when(productDetailCachePort.getAll(List.of(productId)))
                .thenReturn(new LinkedHashMap<>());
        when(productReadPort.findAllByIdIn(List.of(productId)))
                .thenReturn(List.of(product));
        when(productResultFactory.from(product))
                .thenReturn(null);

        List<ProductResult> actual = productCacheQueryService.getProducts(List.of(productId));

        assertThat(actual).isEmpty();
        verify(productDetailCachePort, never()).putAll(anyCollection());
    }

    @Test
    @DisplayName("모든 ID 가 무효하면 빈 목록을 반환한다")
    void getProducts_whenAllIdsInvalid_thenReturnEmptyList() {
        List<Long> requestIds = Arrays.asList(null, -1L, 0L);

        List<ProductResult> actual = productCacheQueryService.getProducts(requestIds);

        assertThat(actual).isEmpty();
        verifyNoInteractions(
                productReadPort,
                productDetailCachePort,
                productResultFactory
        );
    }

    private Product mockProduct(Long id) {
        Product product = mock(Product.class);
        lenient().when(product.getId()).thenReturn(id);
        return product;
    }

    private ProductResult createProductResult(Long id, String name) {
        return new ProductResult(
                id,
                name,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(9000),
                10,
                false,
                "ON_SALE",
                FIXED_UPDATED_AT
        );
    }
}