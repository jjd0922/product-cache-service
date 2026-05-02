package infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.application.cache.RebuildJob;
import com.product.infrastructure.cache.RedisRebuildJobStore;
import com.product.infrastructure.config.ProductCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisRebuildJobStoreTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ProductCacheProperties properties;
    private RedisRebuildJobStore store;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        properties = new ProductCacheProperties();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        store = new RedisRebuildJobStore(redisTemplate, objectMapper, properties);
    }

    @Test
    void createIfAbsentActive_createsJobWhenActiveLockIsAcquired() {
        when(valueOperations.get(properties.getRebuildActiveKey())).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq(properties.getRebuildActiveKey()),
                anyString(),
                eq(Duration.ofSeconds(properties.getRebuildActiveTtlSeconds()))
        )).thenReturn(true);

        Optional<RebuildJob> actual = store.createIfAbsentActive("ALL", 10L);

        assertThat(actual).isPresent();
        assertThat(actual.get().getTotal()).isEqualTo(10L);
        verify(valueOperations).set(startsWith(properties.getRebuildJobKeyPrefix()), anyString(), eq(Duration.ofSeconds(properties.getRebuildJobTtlSeconds())));
    }

    @Test
    void createIfAbsentActive_returnsEmptyWhenActiveLockExists() {
        when(valueOperations.get(properties.getRebuildActiveKey())).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq(properties.getRebuildActiveKey()),
                anyString(),
                any(Duration.class)
        )).thenReturn(false);

        Optional<RebuildJob> actual = store.createIfAbsentActive("ALL", 10L);

        assertThat(actual).isEmpty();
        verify(valueOperations, never()).set(startsWith(properties.getRebuildJobKeyPrefix()), anyString(), any(Duration.class));
    }

    @Test
    void find_returnsRestoredJob() throws Exception {
        RebuildJob job = RebuildJob.queued("ALL", 10L);
        String payload = new ObjectMapper()
                .findAndRegisterModules()
                .writeValueAsString(job.snapshot());

        when(valueOperations.get(properties.getRebuildJobKeyPrefix() + job.getJobId())).thenReturn(payload);

        Optional<RebuildJob> actual = store.find(job.getJobId());

        assertThat(actual).isPresent();
        assertThat(actual.get().getJobId()).isEqualTo(job.getJobId());
        assertThat(actual.get().getTotal()).isEqualTo(10L);
    }

    @Test
    void markSucceeded_savesJobAndReleasesActiveKey() throws Exception {
        RebuildJob job = RebuildJob.queued("ALL", 10L);
        String payload = new ObjectMapper()
                .findAndRegisterModules()
                .writeValueAsString(job.snapshot());

        when(valueOperations.get(properties.getRebuildJobKeyPrefix() + job.getJobId())).thenReturn(payload);
        when(valueOperations.get(properties.getRebuildActiveKey())).thenReturn(job.getJobId().toString());

        store.markSucceeded(job.getJobId(), "done");

        verify(valueOperations).set(eq(properties.getRebuildJobKeyPrefix() + job.getJobId()), anyString(), any(Duration.class));
        verify(redisTemplate).delete(properties.getRebuildActiveKey());
    }
}
