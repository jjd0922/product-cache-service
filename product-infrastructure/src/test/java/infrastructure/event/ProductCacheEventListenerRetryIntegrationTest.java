package infrastructure.event;

import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.port.in.ProductCacheEventUseCase;
import com.product.application.port.out.ProductCacheEventDlqPort;
import com.product.application.port.out.ProductCacheMetricsPort;
import com.product.infrastructure.event.ProductCacheEventListener;
import com.product.infrastructure.event.ProductChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = ProductCacheEventListenerRetryIntegrationTest.TestApplication.class,
        properties = {
                "product.cache.event-retry-max-attempts=3",
                "product.cache.event-retry-initial-delay-millis=1",
                "product.cache.event-retry-multiplier=1.0"
        }
)
class ProductCacheEventListenerRetryIntegrationTest {

    @MockBean
    private ProductCacheEventUseCase productCacheEventUseCase;

    @MockBean
    private ProductCacheEventDlqPort productCacheEventDlqPort;

    @MockBean
    private ProductCacheMetricsPort productCacheMetricsPort;

    @org.springframework.beans.factory.annotation.Autowired
    private ProductCacheEventListener listener;

    @Test
    void handle_whenTemporaryFailure_thenRetryAndRecoverWithoutDlq() {
        ProductCacheChangedCommand command = new ProductCacheChangedCommand(1L, ProductCacheChangeType.UPDATED);
        doThrow(new RuntimeException("temporary failure"))
                .doNothing()
                .when(productCacheEventUseCase)
                .handle(command);

        listener.handle(new ProductChangedEvent(1L, ProductCacheChangeType.UPDATED));

        verify(productCacheEventUseCase, times(2)).handle(command);
        verify(productCacheMetricsPort).recordCacheEventRetry(ProductCacheChangeType.UPDATED.name());
        verifyNoInteractions(productCacheEventDlqPort);
    }

    @Test
    void handle_whenPermanentFailure_thenStoreDlqAfterRetries() {
        ProductCacheChangedCommand command = new ProductCacheChangedCommand(2L, ProductCacheChangeType.DELETED);
        doThrow(new RuntimeException("permanent failure"))
                .when(productCacheEventUseCase)
                .handle(command);

        listener.handle(new ProductChangedEvent(2L, ProductCacheChangeType.DELETED));

        verify(productCacheEventUseCase, times(3)).handle(command);
        verify(productCacheMetricsPort, times(3)).recordCacheEventRetry(ProductCacheChangeType.DELETED.name());
        verify(productCacheEventDlqPort).append(command, "RuntimeException: permanent failure");
        verify(productCacheMetricsPort).recordCacheEventDlq(ProductCacheChangeType.DELETED.name());
    }

    @SpringBootConfiguration
    @EnableRetry
    @Import(ProductCacheEventListener.class)
    static class TestApplication {
    }
}
