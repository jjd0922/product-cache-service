package com.product.infrastructure.event;

import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.port.in.ProductCacheEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCacheEventListener {

    private final ProductCacheEventUseCase productCacheEventUseCase;

    @EventListener
    public void handle(ProductChangedEvent event) {
        if (event == null) {
            return;
        }

        productCacheEventUseCase.handle(new ProductCacheChangedCommand(
                event.productId(),
                event.changeType()
        ));
    }
}
