package com.product.application.service;

import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.application.dto.result.ProductResult;
import com.product.application.factory.ProductResultFactory;
import com.product.application.factory.ProductRuntimeCacheFactory;
import com.product.application.port.out.ProductCacheMetricsPort;
import com.product.application.port.out.ProductCacheSingleFlightLock;
import com.product.application.port.out.ProductCacheSingleFlightLockPort;
import com.product.application.port.out.ProductDetailCachePort;
import com.product.application.port.out.ProductNotFoundCachePort;
import com.product.application.port.out.ProductReadPort;
import com.product.application.port.out.ProductRuntimeCachePort;
import com.product.domain.product.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheQueryServiceTest {

    private static final Instant FIXED_UPDATED_AT = Instant.parse("2026-03-28T00:00:00Z");

    @Mock
    private ProductReadPort productReadPort;

    @Mock
    private ProductDetailCachePort productDetailCachePort;

    @Mock
    private ProductRuntimeCachePort productRuntimeCachePort;

    @Mock
    private ProductNotFoundCachePort productNotFoundCachePort;

    @Mock
    private ProductCacheMetricsPort productCacheMetricsPort;

    @Mock
    private ProductCacheSingleFlightLockPort productCacheSingleFlightLockPort;

    @Mock
    private ProductCacheSingleFlightLock productCacheSingleFlightLock;

    @Mock
    private ProductResultFactory productResultFactory;

    @Mock
    private ProductRuntimeCacheFactory productRuntimeCacheFactory;

    @InjectMocks
    private ProductCacheQueryService productCacheQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(productCacheSingleFlightLockPort.tryLock(anyLong()))
                .thenReturn(Optional.of(productCacheSingleFlightLock));
        lenient().when(productNotFoundCachePort.getAll(anyCollection()))
                .thenReturn(Set.of());
    }

    @Test
    @DisplayName("잘못된 상품 ID 이면 empty 를 반환한다")
    void getProduct_whenInvalidId_thenReturnEmpty() {
        assertThat(productCacheQueryService.getProduct(null)).isEmpty();
        assertThat(productCacheQueryService.getProduct(0L)).isEmpty();
        assertThat(productCacheQueryService.getProduct(-1L)).isEmpty();

        verifyNoInteractions(
                productReadPort,
                productDetailCachePort,
                productRuntimeCachePort,
                productNotFoundCachePort,
                productCacheMetricsPort,
                productCacheSingleFlightLockPort,
                productResultFactory,
                productRuntimeCacheFactory
        );
    }

    @Test
    @DisplayName("detail/runtime cache hit 이면 merge 결과를 반환하고 DB fallback 하지 않는다")
    void getProduct_whenDetailAndRuntimeCacheHit_thenReturnMergedResult() {
        ProductResult cachedDetail = mockResult(1L);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedResult = mockResult(1L);

        when(productDetailCachePort.getAll(List.of(1L))).thenReturn(Map.of(1L, cachedDetail));
        when(productRuntimeCachePort.getAll(List.of(1L))).thenReturn(Map.of(1L, runtime));
        when(cachedDetail.applyRuntime(runtime)).thenReturn(mergedResult);

        Optional<ProductResult> actual = productCacheQueryService.getProduct(1L);

        assertThat(actual).contains(mergedResult);
        verify(productReadPort, never()).findAllByIdIn(anyCollection());
        verify(productDetailCachePort, never()).putAll(anyCollection());
        verify(productRuntimeCachePort, never()).putAll(anyCollection());
        verifyNoInteractions(productResultFactory, productRuntimeCacheFactory);
    }

    @Test
    @DisplayName("cache disabled 이면 cache 조회 없이 DB 만 조회한다")
    void getProducts_whenCacheDisabled_thenReadDbOnly() {
        Long productId = 1L;
        Integer stock = 10;

        Product product = mockProduct(productId, stock);
        ProductResult baseResult = mockResult(productId);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedResult = mockResult(productId);

        ReflectionTestUtils.setField(productCacheQueryService, "cacheEnabled", false);

        when(productReadPort.findAllByIdIn(List.of(productId))).thenReturn(List.of(product));
        when(productResultFactory.from(product)).thenReturn(baseResult);
        when(productRuntimeCacheFactory.from(productId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime);
        when(baseResult.applyRuntime(runtime)).thenReturn(mergedResult);

        List<ProductResult> actual = productCacheQueryService.getProducts(List.of(productId));

        assertThat(actual).containsExactly(mergedResult);
        verify(productReadPort).findAllByIdIn(List.of(productId));
        verify(productCacheMetricsPort).recordDbFallback(1L, 1L);
        verifyNoInteractions(
                productDetailCachePort,
                productRuntimeCachePort,
                productNotFoundCachePort,
                productCacheSingleFlightLockPort
        );
    }

    @Test
    @DisplayName("detail/runtime cache miss 이면 DB fallback 후 두 cache 를 모두 재적재한다")
    void getProduct_whenDetailAndRuntimeCacheMiss_thenFallbackToDbAndRepopulateBothCaches() {
        Long productId = 1L;
        Integer stock = 10;

        Product product = mockProduct(productId, stock);
        ProductResult baseResult = mockResult(productId);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedResult = mockResult(productId);

        when(productDetailCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productRuntimeCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productReadPort.findAllByIdIn(List.of(productId))).thenReturn(List.of(product));
        when(productResultFactory.from(product)).thenReturn(baseResult);
        when(productRuntimeCacheFactory.from(productId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime);
        when(baseResult.applyRuntime(runtime)).thenReturn(mergedResult);

        Optional<ProductResult> actual = productCacheQueryService.getProduct(productId);

        assertThat(actual).contains(mergedResult);
        verify(productReadPort).findAllByIdIn(List.of(productId));
        verify(productDetailCachePort).putAll(argThat(products ->
                products.size() == 1 && products.contains(baseResult)
        ));
        verify(productRuntimeCachePort).putAll(argThat(runtimes ->
                runtimes.size() == 1 && runtimes.contains(runtime)
        ));
    }

    @Test
    @DisplayName("detail cache 조회 실패 시에도 DB fallback 후 self-healing 한다")
    void getProduct_whenDetailCacheReadFails_thenFallbackToDbAndSelfHeal() {
        Long productId = 1L;
        Integer stock = 10;

        Product product = mockProduct(productId, stock);
        ProductResult baseResult = mockResult(productId);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedResult = mockResult(productId);

        when(productDetailCachePort.getAll(List.of(productId))).thenThrow(new RuntimeException("detail redis down"));
        when(productRuntimeCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productReadPort.findAllByIdIn(List.of(productId))).thenReturn(List.of(product));
        when(productResultFactory.from(product)).thenReturn(baseResult);
        when(productRuntimeCacheFactory.from(productId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime);
        when(baseResult.applyRuntime(runtime)).thenReturn(mergedResult);

        Optional<ProductResult> actual = productCacheQueryService.getProduct(productId);

        assertThat(actual).contains(mergedResult);
        verify(productReadPort).findAllByIdIn(List.of(productId));
        verify(productDetailCachePort).putAll(argThat(products ->
                products.size() == 1 && products.contains(baseResult)
        ));
        verify(productRuntimeCachePort).putAll(argThat(runtimes ->
                runtimes.size() == 1 && runtimes.contains(runtime)
        ));
    }

    @Test
    @DisplayName("runtime cache 조회 실패 시 detail cache hit 이어도 DB fallback 하여 runtime cache 를 복구한다")
    void getProduct_whenRuntimeCacheReadFails_thenFallbackToDbAndRepopulateRuntimeOnly() {
        Long productId = 1L;
        Integer stock = 10;

        Product product = mockProduct(productId, stock);
        ProductResult cachedDetail = mockResult(productId);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedOnFirstPass = mockResult(productId);
        ProductResult mergedOnFallback = mockResult(productId);

        when(productDetailCachePort.getAll(List.of(productId))).thenReturn(Map.of(productId, cachedDetail));
        when(productRuntimeCachePort.getAll(List.of(productId))).thenThrow(new RuntimeException("runtime redis down"));
        when(productReadPort.findAllByIdIn(List.of(productId))).thenReturn(List.of(product));
        when(productRuntimeCacheFactory.from(productId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime);

        // 1차 merge: runtime cache miss 이므로 null 로 merge
        when(cachedDetail.applyRuntime(null)).thenReturn(mergedOnFirstPass);
        // 2차 fallback merge: DB fallback 후 생성한 runtime 으로 다시 merge
        when(cachedDetail.applyRuntime(runtime)).thenReturn(mergedOnFallback);

        Optional<ProductResult> actual = productCacheQueryService.getProduct(productId);

        assertThat(actual).contains(mergedOnFallback);
        verify(productReadPort).findAllByIdIn(List.of(productId));
        verify(productResultFactory, never()).from(any());
        verify(productDetailCachePort, never()).putAll(anyCollection());
        verify(productRuntimeCachePort).putAll(argThat(runtimes ->
                runtimes.size() == 1 && runtimes.contains(runtime)
        ));
        verify(cachedDetail).applyRuntime(null);
        verify(cachedDetail).applyRuntime(runtime);
    }

    @Test
    @DisplayName("다건 조회는 중복 제거 후 요청 순서를 유지한다")
    void getProducts_shouldDeduplicateAndKeepRequestOrder() {
        Long cachedProductId = 2L;
        Long fallbackProductId = 1L;
        Long notFoundProductId = 3L;
        Integer stock = 10;

        ProductResult cached2 = mockResult(cachedProductId);
        ProductRuntimeCacheData runtime2 = mock(ProductRuntimeCacheData.class);
        ProductResult merged2 = mockResult(cachedProductId);

        Product product1 = mockProduct(fallbackProductId, stock);
        ProductResult base1 = mockResult(fallbackProductId);
        ProductRuntimeCacheData runtime1 = mock(ProductRuntimeCacheData.class);
        ProductResult merged1 = mockResult(fallbackProductId);

        when(productDetailCachePort.getAll(List.of(cachedProductId, fallbackProductId, notFoundProductId)))
                .thenReturn(Map.of(cachedProductId, cached2));
        when(productRuntimeCachePort.getAll(List.of(cachedProductId, fallbackProductId, notFoundProductId)))
                .thenReturn(Map.of(cachedProductId, runtime2));
        when(cached2.applyRuntime(runtime2)).thenReturn(merged2);

        when(productReadPort.findAllByIdIn(List.of(fallbackProductId))).thenReturn(List.of(product1));
        when(productReadPort.findAllByIdIn(List.of(notFoundProductId))).thenReturn(List.of());
        when(productResultFactory.from(product1)).thenReturn(base1);
        when(productRuntimeCacheFactory.from(fallbackProductId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime1);
        when(base1.applyRuntime(runtime1)).thenReturn(merged1);

        List<Long> requestIds = new ArrayList<>(Arrays.asList(
                cachedProductId, fallbackProductId, cachedProductId, notFoundProductId, null, -1L
        ));

        List<ProductResult> actual = productCacheQueryService.getProducts(requestIds);

        assertThat(actual).containsExactly(merged2, merged1);

        ArgumentCaptor<Collection<ProductResult>> detailCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<ProductRuntimeCacheData>> runtimeCaptor = ArgumentCaptor.forClass(Collection.class);

        verify(productDetailCachePort).putAll(detailCaptor.capture());
        verify(productRuntimeCachePort).putAll(runtimeCaptor.capture());

        assertThat(detailCaptor.getValue()).containsExactly(base1);
        assertThat(runtimeCaptor.getValue()).containsExactly(runtime1);
    }

    @Test
    @DisplayName("동일 JVM 안의 같은 상품 cache miss 는 local single-flight 로 DB fallback 을 1회만 수행한다")
    void getProduct_whenSameProductMissesConcurrently_thenFallbackOnceInSameJvm() throws Exception {
        Long productId = 1L;
        Integer stock = 10;

        Product product = mockProduct(productId, stock);
        ProductResult baseResult = mockResult(productId);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedResult = mockResult(productId);
        CountDownLatch dbFallbackStarted = new CountDownLatch(1);
        CountDownLatch releaseDbFallback = new CountDownLatch(1);

        when(productDetailCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productRuntimeCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productReadPort.findAllByIdIn(List.of(productId))).thenAnswer(invocation -> {
            dbFallbackStarted.countDown();
            assertThat(releaseDbFallback.await(1, TimeUnit.SECONDS)).isTrue();
            return List.of(product);
        });
        when(productResultFactory.from(product)).thenReturn(baseResult);
        when(productRuntimeCacheFactory.from(productId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime);
        when(baseResult.applyRuntime(runtime)).thenReturn(mergedResult);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<ProductResult>> first = executor.submit(() -> productCacheQueryService.getProduct(productId));
            assertThat(dbFallbackStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<Optional<ProductResult>> second = executor.submit(() -> productCacheQueryService.getProduct(productId));
            releaseDbFallback.countDown();

            assertThat(first.get(1, TimeUnit.SECONDS)).contains(mergedResult);
            assertThat(second.get(1, TimeUnit.SECONDS)).contains(mergedResult);
        } finally {
            executor.shutdownNow();
        }

        verify(productReadPort, times(1)).findAllByIdIn(List.of(productId));
    }

    @Test
    @DisplayName("분산 락 획득이 실패해도 DB fallback 으로 degrade 한다")
    void getProduct_whenDistributedLockFails_thenFallbackToDb() {
        Long productId = 1L;
        Integer stock = 10;

        Product product = mockProduct(productId, stock);
        ProductResult baseResult = mockResult(productId);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedResult = mockResult(productId);

        when(productDetailCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productRuntimeCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productCacheSingleFlightLockPort.tryLock(productId))
                .thenThrow(new RuntimeException("redis lock down"));
        when(productReadPort.findAllByIdIn(List.of(productId))).thenReturn(List.of(product));
        when(productResultFactory.from(product)).thenReturn(baseResult);
        when(productRuntimeCacheFactory.from(productId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime);
        when(baseResult.applyRuntime(runtime)).thenReturn(mergedResult);

        Optional<ProductResult> actual = productCacheQueryService.getProduct(productId);

        assertThat(actual).contains(mergedResult);
        verify(productReadPort).findAllByIdIn(List.of(productId));
    }

    @Test
    @DisplayName("DB fallback 동시 호출 수가 bulkhead 한도를 넘으면 요청을 거절하고 메트릭을 기록한다")
    void getProduct_whenDbFallbackBulkheadIsFull_thenRejectFallbackAndRecordMetric() throws Exception {
        Long firstProductId = 1L;
        Long rejectedProductId = 2L;
        Integer stock = 10;

        Product product = mockProduct(firstProductId, stock);
        ProductResult baseResult = mockResult(firstProductId);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedResult = mockResult(firstProductId);
        CountDownLatch dbFallbackStarted = new CountDownLatch(1);
        CountDownLatch releaseDbFallback = new CountDownLatch(1);

        ReflectionTestUtils.setField(productCacheQueryService, "dbFallbackMaxConcurrentCalls", 1);
        ReflectionTestUtils.setField(productCacheQueryService, "dbFallbackMaxWaitMillis", 0L);
        ReflectionTestUtils.setField(productCacheQueryService, "dbFallbackBulkhead", null);

        when(productDetailCachePort.getAll(anyCollection())).thenReturn(Map.of());
        when(productRuntimeCachePort.getAll(anyCollection())).thenReturn(Map.of());
        when(productReadPort.findAllByIdIn(List.of(firstProductId))).thenAnswer(invocation -> {
            dbFallbackStarted.countDown();
            assertThat(releaseDbFallback.await(1, TimeUnit.SECONDS)).isTrue();
            return List.of(product);
        });
        when(productResultFactory.from(product)).thenReturn(baseResult);
        when(productRuntimeCacheFactory.from(firstProductId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime);
        when(baseResult.applyRuntime(runtime)).thenReturn(mergedResult);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<ProductResult>> first = executor.submit(() -> productCacheQueryService.getProduct(firstProductId));
            assertThat(dbFallbackStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<Optional<ProductResult>> rejected = executor.submit(() -> productCacheQueryService.getProduct(rejectedProductId));

            assertThatThrownBy(() -> rejected.get(1, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(com.product.application.common.exception.DbFallbackRejectedException.class);

            releaseDbFallback.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).contains(mergedResult);
        } finally {
            releaseDbFallback.countDown();
            executor.shutdownNow();
        }

        verify(productReadPort, never()).findAllByIdIn(List.of(rejectedProductId));
        verify(productCacheMetricsPort).recordDbFallbackRejected(1L);
    }

    @Test
    @DisplayName("존재하지 않는 상품은 첫 DB fallback 이후 not-found cache 로 보호한다")
    void getProduct_whenProductDoesNotExist_thenStoreNotFoundMarkerAndSkipDbOnMarkerHit() {
        Long productId = 404L;

        when(productDetailCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productRuntimeCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productReadPort.findAllByIdIn(List.of(productId))).thenReturn(List.of());

        Optional<ProductResult> first = productCacheQueryService.getProduct(productId);

        assertThat(first).isEmpty();
        verify(productReadPort).findAllByIdIn(List.of(productId));
        verify(productNotFoundCachePort).put(productId);

        clearInvocations(productReadPort, productDetailCachePort, productRuntimeCachePort, productNotFoundCachePort);
        when(productNotFoundCachePort.getAll(anyCollection())).thenReturn(Set.of(productId));

        Optional<ProductResult> second = productCacheQueryService.getProduct(productId);

        assertThat(second).isEmpty();
        verify(productNotFoundCachePort).getAll(List.of(productId));
        verifyNoInteractions(productReadPort, productDetailCachePort, productRuntimeCachePort);
        verify(productCacheMetricsPort, atLeastOnce()).recordNotFoundCacheHit(1L);
    }

    @Test
    @DisplayName("DB fallback 후 detail cache 재적재 실패가 나도 결과는 반환하고 runtime cache 재적재는 계속 수행한다")
    void getProducts_whenDetailCachePutFails_thenStillReturnResults() {
        Long productId = 1L;
        Integer stock = 10;

        Product product = mockProduct(productId, stock);
        ProductResult baseResult = mockResult(productId);
        ProductRuntimeCacheData runtime = mock(ProductRuntimeCacheData.class);
        ProductResult mergedResult = mockResult(productId);

        when(productDetailCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productRuntimeCachePort.getAll(List.of(productId))).thenReturn(Map.of());
        when(productReadPort.findAllByIdIn(List.of(productId))).thenReturn(List.of(product));
        when(productResultFactory.from(product)).thenReturn(baseResult);
        when(productRuntimeCacheFactory.from(productId, null, stock, FIXED_UPDATED_AT)).thenReturn(runtime);
        when(baseResult.applyRuntime(runtime)).thenReturn(mergedResult);

        doThrow(new RuntimeException("detail put failed"))
                .when(productDetailCachePort).putAll(anyCollection());

        List<ProductResult> actual = productCacheQueryService.getProducts(List.of(productId));

        assertThat(actual).containsExactly(mergedResult);
        verify(productReadPort).findAllByIdIn(List.of(productId));
        verify(productDetailCachePort).putAll(argThat(products ->
                products.size() == 1 && products.contains(baseResult)
        ));
        verify(productRuntimeCachePort).putAll(argThat(runtimes ->
                runtimes.size() == 1 && runtimes.contains(runtime)
        ));
    }

    @Test
    @DisplayName("모든 ID 가 무효하면 빈 목록을 반환한다")
    void getProducts_whenAllIdsInvalid_thenReturnEmptyList() {
        List<Long> requestIds = new ArrayList<>(Arrays.asList(null, -1L, 0L));

        List<ProductResult> actual = productCacheQueryService.getProducts(requestIds);

        assertThat(actual).isEmpty();
        verifyNoInteractions(
                productReadPort,
                productDetailCachePort,
                productRuntimeCachePort,
                productNotFoundCachePort,
                productCacheMetricsPort,
                productCacheSingleFlightLockPort,
                productResultFactory,
                productRuntimeCacheFactory
        );
    }

    private ProductResult mockResult(Long id) {
        ProductResult result = mock(ProductResult.class);
        lenient().when(result.id()).thenReturn(id);
        return result;
    }

    private Product mockProduct(Long id, Integer stock) {
        Product product = mock(Product.class);
        lenient().when(product.getId()).thenReturn(id);
        lenient().when(product.getStock()).thenReturn(stock);
        lenient().when(product.getUpdatedAt()).thenReturn(FIXED_UPDATED_AT);
        return product;
    }
}
