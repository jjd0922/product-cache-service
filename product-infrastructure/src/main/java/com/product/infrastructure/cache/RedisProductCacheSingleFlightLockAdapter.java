package com.product.infrastructure.cache;

import com.product.application.port.out.ProductCacheSingleFlightLock;
import com.product.application.port.out.ProductCacheSingleFlightLockPort;
import com.product.infrastructure.config.ProductCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisProductCacheSingleFlightLockAdapter implements ProductCacheSingleFlightLockPort {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ProductCacheProperties properties;

    @Override
    public Optional<ProductCacheSingleFlightLock> tryLock(Long productId) {
        if (productId == null || productId <= 0) {
            return Optional.empty();
        }

        String key = lockKey(productId);
        String owner = UUID.randomUUID().toString();
        Duration leaseTime = Duration.ofMillis(Math.max(properties.getSingleFlightLockLeaseMillis(), 1L));

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, owner, leaseTime);
        if (!Boolean.TRUE.equals(locked)) {
            return Optional.empty();
        }

        return Optional.of(new RedisProductCacheSingleFlightLock(redisTemplate, key, owner));
    }

    private String lockKey(Long productId) {
        return properties.getSingleFlightLockKeyPrefix() + productId;
    }

    private record RedisProductCacheSingleFlightLock(
            StringRedisTemplate redisTemplate,
            String key,
            String owner
    ) implements ProductCacheSingleFlightLock {

        @Override
        public void close() {
            try {
                redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), owner);
            } catch (RuntimeException e) {
                log.warn("single-flight lock 해제 실패. key={}, message={}", key, e.getMessage());
            }
        }
    }
}
