package infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.application.dto.result.ProductResult;
import com.product.infrastructure.cache.RedisProductDetailCacheAdapter;
import com.product.infrastructure.cache.support.ProductCacheCircuitBreaker;
import com.product.infrastructure.cache.support.ProductCacheKeyGenerator;
import com.product.infrastructure.cache.support.ProductCacheTtlPolicy;
import com.product.infrastructure.cache.support.RedisCacheBatchExecutor;
import com.product.infrastructure.config.ProductCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RedisProductDetailCacheAdapterIntegrationTest.TestApplication.class)
@Testcontainers
class RedisProductDetailCacheAdapterIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private RedisProductDetailCacheAdapter adapter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ProductCacheProperties properties;

    @MockBean
    private ProductCacheKeyGenerator keyGenerator;

    @MockBean
    private RedisCacheBatchExecutor batchExecutor;

    @MockBean
    private ProductCacheTtlPolicy ttlPolicy;

    @MockBean
    private ProductCacheCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        when(properties.getDetailTtlSeconds()).thenReturn(300L);
        when(properties.getPipelineBatchSize()).thenReturn(100);
        when(keyGenerator.detailKey(1L)).thenReturn("product:detail:1");
        when(ttlPolicy.detailTtlSeconds()).thenReturn(300L);
        when(circuitBreaker.executeSupplier(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get()
        );
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(circuitBreaker).executeRunnable(any());

        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("실제 Redis 에 detail cache 를 저장하고 조회하고 삭제할 수 있다")
    void putGetAndEvict_withRealRedis() {
        ProductResult product = new ProductResult(
                1L,
                "상품1",
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                10,
                false,
                "ON_SALE",
                Instant.parse("2026-03-20T00:00:00Z")
        );

        adapter.put(product);

        String rawValue = redisTemplate.opsForValue().get("product:detail:1");
        Long ttl = redisTemplate.getExpire("product:detail:1", TimeUnit.SECONDS);

        assertThat(rawValue).isNotBlank();
        assertThat(ttl).isNotNull();
        assertThat(ttl).isPositive();

        Optional<ProductResult> actual = adapter.get(1L);

        assertThat(actual).isPresent();
        assertThat(actual.get()).isEqualTo(product);

        adapter.evict(1L);

        assertThat(redisTemplate.hasKey("product:detail:1")).isFalse();
        assertThat(adapter.get(1L)).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(RedisProductDetailCacheAdapter.class)
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
