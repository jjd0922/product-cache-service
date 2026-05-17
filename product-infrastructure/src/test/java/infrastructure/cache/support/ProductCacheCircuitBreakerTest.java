package infrastructure.cache.support;

import com.product.infrastructure.cache.support.ProductCacheCircuitBreaker;
import com.product.infrastructure.config.ProductCacheProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductCacheCircuitBreakerTest {

    @Test
    void executeSupplier_whenFailureThresholdReached_thenOpensCircuitAndRecordsStateMetric() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setCircuitFailureRateThreshold(50.0f);
        properties.setCircuitSlidingWindowSize(2);
        properties.setCircuitMinimumNumberOfCalls(2);
        properties.setCircuitWaitDurationInOpenStateMillis(1000L);
        properties.setCircuitPermittedCallsInHalfOpenState(1);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProductCacheCircuitBreaker circuitBreaker = new ProductCacheCircuitBreaker(properties, meterRegistry);

        assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> {
            throw new IllegalStateException("redis down");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> {
            throw new IllegalStateException("redis down");
        })).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> "blocked"))
                .isInstanceOf(CallNotPermittedException.class);

        assertThat(gauge(meterRegistry, "open")).isEqualTo(1.0);
        assertThat(gauge(meterRegistry, "closed")).isEqualTo(0.0);
    }

    private double gauge(SimpleMeterRegistry meterRegistry, String state) {
        return meterRegistry.get("product.cache.circuit.state")
                .tag("circuit", "redis-cache")
                .tag("state", state)
                .gauge()
                .value();
    }
}
