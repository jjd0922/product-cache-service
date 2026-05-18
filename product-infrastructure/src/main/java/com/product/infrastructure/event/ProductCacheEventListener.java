package com.product.infrastructure.event;

import com.product.application.common.failure.FailureReasonBuilder;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.port.in.ProductCacheEventUseCase;
import com.product.application.port.out.ProductCacheEventDlqPort;
import com.product.application.port.out.ProductCacheMetricsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCacheEventListener {

    private final ProductCacheEventUseCase productCacheEventUseCase;
    private final ProductCacheEventDlqPort productCacheEventDlqPort;
    private final ProductCacheMetricsPort productCacheMetricsPort;

    @Async
    @EventListener
    @Retryable(
            retryFor = RuntimeException.class,
            maxAttemptsExpression = "${product.cache.event-retry-max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${product.cache.event-retry-initial-delay-millis:1000}",
                    multiplierExpression = "${product.cache.event-retry-multiplier:2.0}"
            )
    )
    public void handle(ProductChangedEvent event) {
        if (event == null) {
            return;
        }

        ProductCacheChangedCommand command = toCommand(event);
        try {
            productCacheEventUseCase.handle(command);
        } catch (RuntimeException e) {
            productCacheMetricsPort.recordCacheEventRetry(command.changeType().name());
            throw e;
        }
    }

    @Recover
    public void recover(RuntimeException exception, ProductChangedEvent event) {
        if (event == null) {
            return;
        }

        ProductCacheChangedCommand command = toCommand(event);
        productCacheEventDlqPort.append(command, FailureReasonBuilder.from(exception));
        productCacheMetricsPort.recordCacheEventDlq(command.changeType().name());
    }

    private ProductCacheChangedCommand toCommand(ProductChangedEvent event) {
        return new ProductCacheChangedCommand(event.productId(), event.changeType());
    }
}
