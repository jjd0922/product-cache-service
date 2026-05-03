package com.product.application.service;

import com.product.application.common.failure.FailureReasonBuilder;
import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.port.in.ProductCacheEventUseCase;
import com.product.application.port.out.ProductCacheMetricsPort;
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
    private final ProductCacheMetricsPort productCacheMetricsPort;

    @Override
    public void handle(ProductCacheChangedCommand command) {
        if (!isValid(command)) {
            log.debug("event=product_cache_event_ignored reason=invalid_command command={}", command);
            return;
        }

        try {
            if (command.changeType() == ProductCacheChangeType.DELETED) {
                productCacheRefreshService.evict(command.productId());
                productCacheMetricsPort.recordCacheEventHandled(command.changeType().name(), false);
                return;
            }

            Optional<Product> product = productReadPort.findById(command.productId());
            if (product.isPresent()) {
                productCacheRefreshService.refresh(product.get());
            } else {
                productCacheRefreshService.evict(command.productId());
            }

            productCacheMetricsPort.recordCacheEventHandled(command.changeType().name(), false);
        } catch (RuntimeException e) {
            productCacheMetricsPort.recordCacheEventHandled(command.changeType().name(), true);
            log.error(
                    "event=product_cache_event_failed productId={} changeType={} failureReason={}",
                    command.productId(),
                    command.changeType(),
                    FailureReasonBuilder.from(e),
                    e
            );
            throw e;
        }
    }

    private boolean isValid(ProductCacheChangedCommand command) {
        return command != null
                && command.productId() != null
                && command.productId() > 0
                && command.changeType() != null;
    }
}
