package com.product.application.service;

import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.port.out.ProductCacheMetricsPort;
import com.product.application.port.out.ProductReadPort;
import com.product.domain.product.model.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ProductCacheEventServiceTest {

    @Mock
    private ProductReadPort productReadPort;

    @Mock
    private ProductCacheRefreshService productCacheRefreshService;

    @Mock
    private ProductCacheMetricsPort productCacheMetricsPort;

    @InjectMocks
    private ProductCacheEventService productCacheEventService;

    @Test
    void handle_whenUpdatedAndProductExists_thenRefreshCache() {
        Product product = product(1L);
        when(productReadPort.findById(1L)).thenReturn(Optional.of(product));

        productCacheEventService.handle(new ProductCacheChangedCommand(1L, ProductCacheChangeType.UPDATED));

        verify(productReadPort).findById(1L);
        verify(productCacheRefreshService).refresh(product);
        verify(productCacheRefreshService, never()).evict(anyLong());
        verify(productCacheMetricsPort).recordCacheEventHandled("UPDATED", false);
    }

    @Test
    void handle_whenUpdatedAndProductMissing_thenEvictCache() {
        when(productReadPort.findById(1L)).thenReturn(Optional.empty());

        productCacheEventService.handle(new ProductCacheChangedCommand(1L, ProductCacheChangeType.UPDATED));

        verify(productReadPort).findById(1L);
        verify(productCacheRefreshService).evict(1L);
        verify(productCacheRefreshService, never()).refresh(any());
        verify(productCacheMetricsPort).recordCacheEventHandled("UPDATED", false);
    }

    @Test
    void handle_whenDeleted_thenEvictCacheWithoutDbLookup() {
        productCacheEventService.handle(new ProductCacheChangedCommand(1L, ProductCacheChangeType.DELETED));

        verify(productCacheRefreshService).evict(1L);
        verify(productCacheMetricsPort).recordCacheEventHandled("DELETED", false);
        verifyNoInteractions(productReadPort);
    }

    @Test
    void handle_whenCommandIsInvalid_thenDoNothing() {
        productCacheEventService.handle(null);
        productCacheEventService.handle(new ProductCacheChangedCommand(null, ProductCacheChangeType.UPDATED));
        productCacheEventService.handle(new ProductCacheChangedCommand(0L, ProductCacheChangeType.UPDATED));
        productCacheEventService.handle(new ProductCacheChangedCommand(1L, null));

        verifyNoInteractions(productReadPort, productCacheRefreshService, productCacheMetricsPort);
    }

    @Test
    void handle_whenRefreshFails_thenRecordFailureAndRethrow() {
        Product product = product(1L);
        when(productReadPort.findById(1L)).thenReturn(Optional.of(product));
        doThrow(new RuntimeException("redis down"))
                .when(productCacheRefreshService)
                .refresh(product);

        assertThatThrownBy(() -> productCacheEventService.handle(new ProductCacheChangedCommand(1L, ProductCacheChangeType.UPDATED)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis down");

        verify(productCacheMetricsPort).recordCacheEventHandled("UPDATED", true);
        verify(productCacheMetricsPort, never()).recordCacheEventHandled("UPDATED", false);
    }

    private Product product(Long id) {
        return new Product(
                id,
                "product-" + id,
                new BigDecimal("10000.00"),
                10,
                Instant.parse("2026-03-20T00:00:00Z")
        );
    }
}
