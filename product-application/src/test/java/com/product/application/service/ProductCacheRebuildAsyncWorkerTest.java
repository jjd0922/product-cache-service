package com.product.application.service;

import com.product.application.cache.RebuildRequest;
import com.product.application.port.out.ProductReadPort;
import com.product.application.port.out.RebuildJobStore;
import com.product.domain.product.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyList;

@ExtendWith(MockitoExtension.class)
class ProductCacheRebuildAsyncWorkerTest {

    @Mock
    private ProductReadPort productReadPort;

    @Mock
    private ProductCacheRefreshService productCacheRefreshService;

    @Mock
    private RebuildJobStore rebuildJobStore;

    @InjectMocks
    private ProductCacheRebuildAsyncWorker productCacheRebuildAsyncWorker;

    @Test
    @DisplayName("모든 chunk refresh 가 성공하면 진행률을 갱신하고 성공 처리한다")
    void rebuild_whenAllSuccess_thenUpdateProgressAndMarkSucceeded() {
        UUID jobId = UUID.randomUUID();

        RebuildRequest request = mock(RebuildRequest.class);
        when(request.isEmpty()).thenReturn(false);
        when(request.chunkSize()).thenReturn(1);
        when(request.targetProductIds()).thenReturn(List.of(1L, 2L, 3L));

        Product product1 = mock(Product.class);
        Product product2 = mock(Product.class);
        Product product3 = mock(Product.class);

        when(productReadPort.findAllByIdIn(List.of(1L))).thenReturn(List.of(product1));
        when(productReadPort.findAllByIdIn(List.of(2L))).thenReturn(List.of(product2));
        when(productReadPort.findAllByIdIn(List.of(3L))).thenReturn(List.of(product3));

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, request))
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(rebuildJobStore, productReadPort, productCacheRefreshService);

        inOrder.verify(rebuildJobStore).markRunning(eq(jobId), argThat(message ->
                message.contains("total=3") &&
                        message.contains("chunkSize=1") &&
                        message.contains("chunks=3")
        ));

        inOrder.verify(productReadPort).findAllByIdIn(List.of(1L));
        inOrder.verify(productCacheRefreshService).refreshAll(argThat(products ->
                products.size() == 1 && products.contains(product1)
        ));
        inOrder.verify(rebuildJobStore).updateProgress(eq(jobId), eq(1L), argThat(message ->
                message.contains("청크 1/3 처리 완료") &&
                        message.contains("requested=1") &&
                        message.contains("loaded=1")
        ));

        inOrder.verify(productReadPort).findAllByIdIn(List.of(2L));
        inOrder.verify(productCacheRefreshService).refreshAll(argThat(products ->
                products.size() == 1 && products.contains(product2)
        ));
        inOrder.verify(rebuildJobStore).updateProgress(eq(jobId), eq(2L), argThat(message ->
                message.contains("청크 2/3 처리 완료") &&
                        message.contains("requested=1") &&
                        message.contains("loaded=1")
        ));

        inOrder.verify(productReadPort).findAllByIdIn(List.of(3L));
        inOrder.verify(productCacheRefreshService).refreshAll(argThat(products ->
                products.size() == 1 && products.contains(product3)
        ));
        inOrder.verify(rebuildJobStore).updateProgress(eq(jobId), eq(3L), argThat(message ->
                message.contains("청크 3/3 처리 완료") &&
                        message.contains("requested=1") &&
                        message.contains("loaded=1")
        ));

        inOrder.verify(rebuildJobStore).markSucceeded(eq(jobId), argThat(message ->
                message.contains("캐시 재빌드가 완료되었습니다.") &&
                        message.contains("total=3") &&
                        message.contains("processed=3")
        ));
    }

    @Test
    @DisplayName("중간 chunk 에서 refresh 실패가 발생하면 failed 처리한다")
    void rebuild_whenRefreshFails_thenMarkFailed() {
        UUID jobId = UUID.randomUUID();
        RebuildRequest request = new RebuildRequest(List.of(1L, 2L), 1, "IDS(2)");

        Product product1 = mock(Product.class);
        Product product2 = mock(Product.class);

        when(productReadPort.findAllByIdIn(List.of(1L))).thenReturn(List.of(product1));
        when(productReadPort.findAllByIdIn(List.of(2L))).thenReturn(List.of(product2));

        doNothing()
                .doThrow(new RuntimeException("refresh failed"))
                .when(productCacheRefreshService)
                .refreshAll(anyList());

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, request))
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(rebuildJobStore, productReadPort, productCacheRefreshService);

        inOrder.verify(rebuildJobStore).markRunning(eq(jobId), argThat(message ->
                message.contains("total=2") &&
                        message.contains("chunkSize=1") &&
                        message.contains("chunks=2")
        ));

        inOrder.verify(productReadPort).findAllByIdIn(List.of(1L));
        inOrder.verify(productCacheRefreshService).refreshAll(List.of(product1));
        inOrder.verify(rebuildJobStore).updateProgress(eq(jobId), eq(1L), argThat(message ->
                message.contains("청크 1/2 처리 완료")
        ));

        inOrder.verify(productReadPort).findAllByIdIn(List.of(2L));
        inOrder.verify(productCacheRefreshService).refreshAll(List.of(product2));

        verify(rebuildJobStore, never()).updateProgress(eq(jobId), eq(2L), anyString());
        verify(rebuildJobStore).markFailed(
                jobId,
                "캐시 재빌드가 실패했습니다.",
                "RuntimeException: refresh failed"
        );
        verify(rebuildJobStore, never()).markSucceeded(any(), any());
    }

    @Test
    @DisplayName("요청이 null 이면 failed 처리 후 종료한다")
    void rebuild_whenRequestIsNull_thenMarkFailed() {
        UUID jobId = UUID.randomUUID();

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, null))
                .doesNotThrowAnyException();

        verify(rebuildJobStore).markFailed(
                jobId,
                "캐시 재빌드가 실패했습니다.",
                "RebuildRequest 가 null 입니다."
        );
        verifyNoInteractions(productReadPort, productCacheRefreshService);
    }

    @Test
    @DisplayName("요청이 비어 있으면 성공 처리 후 종료한다")
    void rebuild_whenRequestIsEmpty_thenMarkSucceeded() {
        UUID jobId = UUID.randomUUID();

        RebuildRequest request = mock(RebuildRequest.class);
        when(request.isEmpty()).thenReturn(true);

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, request))
                .doesNotThrowAnyException();

        verify(rebuildJobStore).markSucceeded(jobId, "재빌드 대상 상품이 없습니다.");
        verifyNoInteractions(productReadPort, productCacheRefreshService);
    }

    @Test
    @DisplayName("chunkSize 가 1 미만이면 failed 처리 후 종료한다")
    void rebuild_whenChunkSizeIsInvalid_thenMarkFailed() {
        UUID jobId = UUID.randomUUID();

        RebuildRequest request = mock(RebuildRequest.class);
        when(request.isEmpty()).thenReturn(false);
        when(request.chunkSize()).thenReturn(0);

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, request))
                .doesNotThrowAnyException();

        verify(rebuildJobStore).markFailed(
                jobId,
                "캐시 재빌드가 실패했습니다.",
                "chunkSize 는 1 이상이어야 합니다. chunkSize=0"
        );
        verifyNoInteractions(productReadPort, productCacheRefreshService);
    }
}