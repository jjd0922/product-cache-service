package com.product.infrastructure.cache.support;

import com.product.infrastructure.config.ProductCacheProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@Component
public class ProductCacheCircuitBreaker {

    private static final String CIRCUIT_NAME = "redis-cache";

    private final CircuitBreaker circuitBreaker;
    private final AtomicReference<CircuitBreaker.State> currentState;

    public ProductCacheCircuitBreaker(ProductCacheProperties properties, MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getCircuitFailureRateThreshold())
                .slidingWindowSize(Math.max(properties.getCircuitSlidingWindowSize(), 1))
                .minimumNumberOfCalls(Math.max(properties.getCircuitMinimumNumberOfCalls(), 1))
                .waitDurationInOpenState(Duration.ofMillis(
                        Math.max(properties.getCircuitWaitDurationInOpenStateMillis(), 1L)
                ))
                .permittedNumberOfCallsInHalfOpenState(Math.max(
                        properties.getCircuitPermittedCallsInHalfOpenState(),
                        1
                ))
                .build();

        this.circuitBreaker = CircuitBreaker.of(CIRCUIT_NAME, config);
        this.currentState = new AtomicReference<>(circuitBreaker.getState());
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    currentState.set(event.getStateTransition().getToState());
                    log.warn(
                            "event=cache_circuit_state_transition circuit={} from={} to={}",
                            CIRCUIT_NAME,
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()
                    );
                });

        registerStateGauges(meterRegistry);
    }

    public <T> T executeSupplier(Supplier<T> supplier) {
        return circuitBreaker.executeSupplier(supplier);
    }

    public void executeRunnable(Runnable runnable) {
        circuitBreaker.executeRunnable(runnable);
    }

    private void registerStateGauges(MeterRegistry meterRegistry) {
        for (CircuitBreaker.State state : CircuitBreaker.State.values()) {
            Gauge.builder(
                            "product.cache.circuit.state",
                            currentState,
                            value -> value.get() == state ? 1.0 : 0.0
                    )
                    .tag("circuit", CIRCUIT_NAME)
                    .tag("state", state.name().toLowerCase())
                    .register(meterRegistry);
        }
    }
}
