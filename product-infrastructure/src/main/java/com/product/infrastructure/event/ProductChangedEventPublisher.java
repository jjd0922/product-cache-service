package com.product.infrastructure.event;

import com.product.application.dto.command.ProductCacheChangeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductChangedEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(Long productId, ProductCacheChangeType changeType) {
        applicationEventPublisher.publishEvent(new ProductChangedEvent(productId, changeType));
    }
}
