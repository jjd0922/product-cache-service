package com.product.application.service;

import com.product.application.cache.RebuildRequest;
import com.product.application.port.out.ProductCacheMetricsPort;
import com.product.application.port.out.ProductReadPort;
import com.product.application.port.out.RebuildJobStore;
import com.product.domain.product.model.Product;
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

@ExtendWith(MockitoExtension.class)
class ProductCacheRebuildAsyncWorkerTest {

    @Mock
    private ProductReadPort productReadPort;

    @Mock
    private ProductCacheRefreshService productCacheRefreshService;

    @Mock
    private RebuildJobStore rebuildJobStore;

    @Mock
    private ProductCacheMetricsPort productCacheMetricsPort;

    @InjectMocks
    private ProductCacheRebuildAsyncWorker productCacheRebuildAsyncWorker;

    @Test
    void rebuild_whenRequestedIdsSucceed_thenProcessChunksAndMarkSucceeded() {
        UUID jobId = UUID.randomUUID();
        RebuildRequest request = new RebuildRequest(List.of(1L, 2L, 3L), 1, "IDS(3)");

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
        inOrder.verify(productCacheRefreshService).refreshAll(List.of(product1));
        inOrder.verify(rebuildJobStore).updateProgress(eq(jobId), eq(1L), argThat(message ->
                message.contains("Chunk 1/3 completed") &&
                        message.contains("requested=1") &&
                        message.contains("loaded=1")
        ));

        inOrder.verify(productReadPort).findAllByIdIn(List.of(2L));
        inOrder.verify(productCacheRefreshService).refreshAll(List.of(product2));
        inOrder.verify(rebuildJobStore).updateProgress(eq(jobId), eq(2L), argThat(message ->
                message.contains("Chunk 2/3 completed")
        ));

        inOrder.verify(productReadPort).findAllByIdIn(List.of(3L));
        inOrder.verify(productCacheRefreshService).refreshAll(List.of(product3));
        inOrder.verify(rebuildJobStore).updateProgress(eq(jobId), eq(3L), argThat(message ->
                message.contains("Chunk 3/3 completed")
        ));

        inOrder.verify(rebuildJobStore).markSucceeded(eq(jobId), argThat(message ->
                message.contains("Cache rebuild completed") &&
                        message.contains("total=3") &&
                        message.contains("processed=3")
        ));
    }

    @Test
    void rebuild_whenAllProducts_thenReadsIdsByKeysetChunks() {
        UUID jobId = UUID.randomUUID();
        RebuildRequest request = RebuildRequest.allProducts(3L, 2);

        Product product1 = mock(Product.class);
        Product product2 = mock(Product.class);
        Product product3 = mock(Product.class);

        when(productReadPort.findIdsAfter(0L, 2)).thenReturn(List.of(1L, 2L));
        when(productReadPort.findIdsAfter(2L, 2)).thenReturn(List.of(3L));
        when(productReadPort.findIdsAfter(3L, 2)).thenReturn(List.of());
        when(productReadPort.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(product1, product2));
        when(productReadPort.findAllByIdIn(List.of(3L))).thenReturn(List.of(product3));

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, request))
                .doesNotThrowAnyException();

        verify(productReadPort).findIdsAfter(0L, 2);
        verify(productReadPort).findIdsAfter(2L, 2);
        verify(productReadPort).findIdsAfter(3L, 2);
        verify(productReadPort).findAllByIdIn(List.of(1L, 2L));
        verify(productReadPort).findAllByIdIn(List.of(3L));
        verify(rebuildJobStore).markSucceeded(eq(jobId), argThat(message ->
                message.contains("total=3") && message.contains("processed=3")
        ));
    }

    @Test
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

        verify(rebuildJobStore).markFailed(
                jobId,
                "Cache rebuild failed.",
                "RuntimeException: refresh failed"
        );
        verify(rebuildJobStore, never()).markSucceeded(any(), any());
    }

    @Test
    void rebuild_whenRequestIsNull_thenMarkFailed() {
        UUID jobId = UUID.randomUUID();

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, null))
                .doesNotThrowAnyException();

        verify(rebuildJobStore).markFailed(
                jobId,
                "Cache rebuild failed.",
                "RebuildRequest must not be null"
        );
        verifyNoInteractions(productReadPort, productCacheRefreshService);
    }

    @Test
    void rebuild_whenRequestIsEmpty_thenMarkSucceeded() {
        UUID jobId = UUID.randomUUID();
        RebuildRequest request = new RebuildRequest(List.of(), 1, "IDS(0)");

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, request))
                .doesNotThrowAnyException();

        verify(rebuildJobStore).markSucceeded(jobId, "No products to rebuild.");
        verifyNoInteractions(productReadPort, productCacheRefreshService);
    }

    @Test
    void rebuild_whenChunkSizeIsInvalid_thenMarkFailed() {
        UUID jobId = UUID.randomUUID();

        RebuildRequest request = mock(RebuildRequest.class);
        when(request.isEmpty()).thenReturn(false);
        when(request.chunkSize()).thenReturn(0);

        assertThatCode(() -> productCacheRebuildAsyncWorker.rebuild(jobId, request))
                .doesNotThrowAnyException();

        verify(rebuildJobStore).markFailed(
                jobId,
                "Cache rebuild failed.",
                "chunkSize must be greater than or equal to 1. chunkSize=0"
        );
        verifyNoInteractions(productReadPort, productCacheRefreshService);
    }
}
