package infrastructure.event;

import com.product.application.dto.command.ProductCacheChangeType;
import com.product.infrastructure.event.ProductChangedEvent;
import com.product.infrastructure.event.ProductChangedEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

class ProductChangedEventPublisherTest {

    private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
    private final ProductChangedEventPublisher publisher = new ProductChangedEventPublisher(applicationEventPublisher);

    @Test
    void publish_publishesProductChangedEvent() {
        publisher.publish(1L, ProductCacheChangeType.DELETED);

        verify(applicationEventPublisher).publishEvent(new ProductChangedEvent(1L, ProductCacheChangeType.DELETED));
    }
}
