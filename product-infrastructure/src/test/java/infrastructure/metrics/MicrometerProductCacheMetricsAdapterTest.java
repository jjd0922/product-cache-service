package infrastructure.metrics;

import com.product.infrastructure.metrics.MicrometerProductCacheMetricsAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerProductCacheMetricsAdapterTest {

    private SimpleMeterRegistry meterRegistry;
    private MicrometerProductCacheMetricsAdapter adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        adapter = new MicrometerProductCacheMetricsAdapter(meterRegistry);
    }

    @Test
    void recordCacheRead_recordsHitAndMissCounts() {
        adapter.recordCacheRead("detail", 5L, 3L, false);

        assertThat(counter("product.cache.read.items", "cache", "detail", "result", "hit")).isEqualTo(3.0);
        assertThat(counter("product.cache.read.items", "cache", "detail", "result", "miss")).isEqualTo(2.0);
    }

    @Test
    void recordCacheWrite_recordsSuccessAndErrorCounts() {
        adapter.recordCacheWrite("runtime", 2L, false);
        adapter.recordCacheWrite("runtime", 1L, true);

        assertThat(counter("product.cache.write.items", "cache", "runtime", "result", "success")).isEqualTo(2.0);
        assertThat(counter("product.cache.write.items", "cache", "runtime", "result", "error")).isEqualTo(1.0);
    }

    @Test
    void recordRebuildCompleted_recordsJobAndDuration() {
        adapter.recordRebuildCompleted(10L, 9L, 123L);

        assertThat(counter("product.cache.rebuild.jobs", "result", "success")).isEqualTo(1.0);
        assertThat(counter("product.cache.rebuild.items", "result", "total")).isEqualTo(10.0);
        assertThat(timerCount("product.cache.rebuild.duration")).isEqualTo(1L);
    }

    @Test
    void recordCacheEventHandled_recordsChangeTypeAndResult() {
        adapter.recordCacheEventHandled("UPDATED", false);
        adapter.recordCacheEventHandled("DELETED", true);

        assertThat(counter("product.cache.event.handled", "changeType", "UPDATED", "result", "success")).isEqualTo(1.0);
        assertThat(counter("product.cache.event.handled", "changeType", "DELETED", "result", "error")).isEqualTo(1.0);
    }

    private double counter(String name, String... tags) {
        return meterRegistry.get(name).tags(tags).counter().count();
    }

    private long timerCount(String name) {
        return meterRegistry.get(name).timer().count();
    }
}
