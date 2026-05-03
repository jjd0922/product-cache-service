package infrastructure.event;

import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.port.in.ProductCacheEventUseCase;
import com.product.infrastructure.event.ProductCacheEventListener;
import com.product.infrastructure.event.ProductChangedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ProductCacheEventListenerTest {

    private final ProductCacheEventUseCase productCacheEventUseCase = mock(ProductCacheEventUseCase.class);
    private final ProductCacheEventListener listener = new ProductCacheEventListener(productCacheEventUseCase);

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
}
