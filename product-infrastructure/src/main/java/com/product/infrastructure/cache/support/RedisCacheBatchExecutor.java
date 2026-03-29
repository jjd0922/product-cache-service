package com.product.infrastructure.cache.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisCacheBatchExecutor {

    private final StringRedisTemplate redisTemplate;

    public void setExBatch(List<CacheEntry> entries, long ttlSeconds) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            executeSetExBatch(connection, entries, ttlSeconds);
            return null;
        });
    }

    private void executeSetExBatch(
            RedisConnection connection,
            List<CacheEntry> entries,
            long ttlSeconds
    ) {
        for (CacheEntry entry : entries) {
            connection.stringCommands().setEx(
                    entry.key().getBytes(StandardCharsets.UTF_8),
                    ttlSeconds,
                    entry.payload().getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    public record CacheEntry(
            String key,
            String payload
    ) {
    }
}