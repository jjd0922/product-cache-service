package com.product.application.service;

import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.port.in.ProductCacheEventUseCase;
import com.product.application.port.out.ProductReadPort;
import com.product.domain.product.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheEventService implements ProductCacheEventUseCase {

    private final ProductReadPort productReadPort;
    private final ProductCacheRefreshService productCacheRefreshService;

    @Override
    public void handle(ProductCacheChangedCommand command) {
        if (!isValid(command)) {
            log.debug("event=product_cache_event_ignored reason=invalid_command command={}", command);
            return;
        }

        if (command.changeType() == ProductCacheChangeType.DELETED) {
            productCacheRefreshService.evict(command.productId());
            return;
        }

        Optional<Product> product = productReadPort.findById(command.productId());
        if (product.isPresent()) {
            productCacheRefreshService.refresh(product.get());
            return;
        }

        productCacheRefreshService.evict(command.productId());
    }

    private boolean isValid(ProductCacheChangedCommand command) {
        return command != null
                && command.productId() != null
                && command.productId() > 0
                && command.changeType() != null;
    }
}
