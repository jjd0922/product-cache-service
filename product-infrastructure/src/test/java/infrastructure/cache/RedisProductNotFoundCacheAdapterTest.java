package infrastructure.cache;

import com.product.infrastructure.cache.RedisProductNotFoundCacheAdapter;
import com.product.infrastructure.cache.support.ProductCacheKeyGenerator;
import com.product.infrastructure.config.ProductCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisProductNotFoundCacheAdapterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ProductCacheProperties properties;
    private ProductCacheKeyGenerator keyGenerator;
    private RedisProductNotFoundCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        properties = new ProductCacheProperties();
        keyGenerator = mock(ProductCacheKeyGenerator.class);
        adapter = new RedisProductNotFoundCacheAdapter(redisTemplate, properties, keyGenerator);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getAll_returnsMarkedProductIdsOnly() {
        when(keyGenerator.notFoundKey(1L)).thenReturn("product:notfound:1");
        when(keyGenerator.notFoundKey(2L)).thenReturn("product:notfound:2");
        when(valueOperations.multiGet(List.of("product:notfound:1", "product:notfound:2")))
                .thenReturn(Arrays.asList("1", null));

        Set<Long> actual = adapter.getAll(List.of(1L, 2L));

        assertThat(actual).containsExactly(1L);
    }

    @Test
    void put_savesMarkerWithConfiguredTtl() {
        when(keyGenerator.notFoundKey(1L)).thenReturn("product:notfound:1");

        adapter.put(1L);

        verify(valueOperations).set(
                "product:notfound:1",
                "1",
                Duration.ofSeconds(properties.getNotFoundTtlSeconds())
        );
    }

    @Test
    void evict_deletesMarkerKey() {
        when(keyGenerator.notFoundKey(1L)).thenReturn("product:notfound:1");

        adapter.evict(1L);

        verify(redisTemplate).delete("product:notfound:1");
    }

    @Test
    void evictAll_deletesNormalizedMarkerKeys() {
        when(keyGenerator.notFoundKey(1L)).thenReturn("product:notfound:1");
        when(keyGenerator.notFoundKey(2L)).thenReturn("product:notfound:2");

        adapter.evictAll(Arrays.asList(1L, null, 2L, 1L, -1L));

        verify(redisTemplate).delete(List.of("product:notfound:1", "product:notfound:2"));
    }

    @Test
    void invalidIds_doNothing() {
        assertThat(adapter.getAll(Arrays.asList(null, 0L, -1L))).isEmpty();
        adapter.put(0L);
        adapter.evict(null);
        adapter.evictAll(Arrays.asList(null, -1L));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).delete(anyCollection());
    }
}
