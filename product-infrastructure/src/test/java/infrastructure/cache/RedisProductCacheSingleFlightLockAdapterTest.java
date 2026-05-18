package infrastructure.cache;

import com.product.application.port.out.ProductCacheSingleFlightLock;
import com.product.infrastructure.cache.RedisProductCacheSingleFlightLockAdapter;
import com.product.infrastructure.config.ProductCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisProductCacheSingleFlightLockAdapterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ProductCacheProperties properties;
    private RedisProductCacheSingleFlightLockAdapter adapter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        properties = new ProductCacheProperties();
        adapter = new RedisProductCacheSingleFlightLockAdapter(redisTemplate, properties);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void tryLock_returnsLockWhenRedisLockIsAcquired() {
        when(valueOperations.setIfAbsent(
                eq(properties.getSingleFlightLockKeyPrefix() + "1"),
                anyString(),
                eq(Duration.ofMillis(properties.getSingleFlightLockLeaseMillis()))
        )).thenReturn(true);

        Optional<ProductCacheSingleFlightLock> actual = adapter.tryLock(1L);

        assertThat(actual).isPresent();
    }

    @Test
    void tryLock_returnsEmptyWhenRedisLockAlreadyExists() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        Optional<ProductCacheSingleFlightLock> actual = adapter.tryLock(1L);

        assertThat(actual).isEmpty();
    }

    @Test
    void close_releasesOnlyOwnedLockWithLuaScript() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        ProductCacheSingleFlightLock lock = adapter.tryLock(1L).orElseThrow();
        lock.close();

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(properties.getSingleFlightLockKeyPrefix() + "1")),
                anyString()
        );
    }

    @Test
    void tryLock_returnsEmptyWhenProductIdIsInvalid() {
        assertThat(adapter.tryLock(null)).isEmpty();
        assertThat(adapter.tryLock(0L)).isEmpty();

        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }
}
