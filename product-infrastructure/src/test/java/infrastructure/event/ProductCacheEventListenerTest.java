package infrastructure.event;

import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.port.in.ProductCacheEventUseCase;
import com.product.application.port.out.ProductCacheEventDlqPort;
import com.product.application.port.out.ProductCacheMetricsPort;
import com.product.infrastructure.event.ProductCacheEventListener;
import com.product.infrastructure.event.ProductChangedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ProductCacheEventListenerTest {

    private final ProductCacheEventUseCase productCacheEventUseCase = mock(ProductCacheEventUseCase.class);
    private final ProductCacheEventDlqPort productCacheEventDlqPort = mock(ProductCacheEventDlqPort.class);
    private final ProductCacheMetricsPort productCacheMetricsPort = mock(ProductCacheMetricsPort.class);
    private final ProductCacheEventListener listener = new ProductCacheEventListener(
            productCacheEventUseCase,
            productCacheEventDlqPort,
            productCacheMetricsPort
    );

    @Test
    void handle_whenEventExists_thenDelegatesToUseCase() {
        listener.handle(new ProductChangedEvent(1L, ProductCacheChangeType.UPDATED));

        verify(productCacheEventUseCase).handle(new ProductCacheChangedCommand(1L, ProductCacheChangeType.UPDATED));
    }

    @Test
    void handle_whenEventIsNull_thenDoNothing() {
        listener.handle(null);

        verifyNoInteractions(productCacheEventUseCase);
    }

    @Test
    void handle_whenUseCaseFails_thenRecordRetryAndRethrow() {
        doThrow(new RuntimeException("redis down"))
                .when(productCacheEventUseCase)
                .handle(new ProductCacheChangedCommand(1L, ProductCacheChangeType.UPDATED));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        listener.handle(new ProductChangedEvent(1L, ProductCacheChangeType.UPDATED)))
                .isInstanceOf(RuntimeException.class);

        verify(productCacheMetricsPort).recordCacheEventRetry(ProductCacheChangeType.UPDATED.name());
    }

    @Test
    void recover_whenRetriesExhausted_thenAppendDlqAndRecordMetric() {
        RuntimeException exception = new RuntimeException("permanent failure");

        listener.recover(exception, new ProductChangedEvent(1L, ProductCacheChangeType.DELETED));

        verify(productCacheEventDlqPort).append(
                new ProductCacheChangedCommand(1L, ProductCacheChangeType.DELETED),
                "RuntimeException: permanent failure"
        );
        verify(productCacheMetricsPort).recordCacheEventDlq(ProductCacheChangeType.DELETED.name());
    }
}
