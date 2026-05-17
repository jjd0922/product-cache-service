package com.product.infrastructure.event;

import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.dto.result.ProductCacheEventDlqResult;
import com.product.application.port.out.ProductCacheEventDlqPort;
import com.product.infrastructure.config.ProductCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisProductCacheEventDlqAdapter implements ProductCacheEventDlqPort {

    private static final String PRODUCT_ID = "productId";
    private static final String CHANGE_TYPE = "changeType";
    private static final String FAILURE_REASON = "failureReason";
    private static final String CREATED_AT = "createdAt";

    private final StringRedisTemplate redisTemplate;
    private final ProductCacheProperties properties;

    @Override
    public void append(ProductCacheChangedCommand command, String failureReason) {
        if (command == null || command.productId() == null || command.changeType() == null) {
            return;
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put(PRODUCT_ID, String.valueOf(command.productId()));
        body.put(CHANGE_TYPE, command.changeType().name());
        body.put(FAILURE_REASON, safe(failureReason));
        body.put(CREATED_AT, Instant.now().toString());

        redisTemplate.opsForStream().add(StreamRecords.newRecord()
                .in(properties.getEventDlqStreamKey())
                .ofMap(body));
    }

    @Override
    public List<ProductCacheEventDlqResult> findAll(int limit) {
        int count = Math.max(1, Math.min(limit, Math.max(properties.getEventDlqMaxReadCount(), 1)));
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(properties.getEventDlqStreamKey(), Range.unbounded());

        if (records == null || records.isEmpty()) {
            return List.of();
        }

        return records.stream()
                .limit(count)
                .map(this::toResult)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<ProductCacheEventDlqResult> find(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }

        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(properties.getEventDlqStreamKey(), Range.closed(eventId, eventId));

        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }

        return toResult(records.get(0));
    }

    @Override
    public void delete(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }

        redisTemplate.opsForStream().delete(properties.getEventDlqStreamKey(), RecordId.of(eventId));
    }

    private Optional<ProductCacheEventDlqResult> toResult(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        try {
            return Optional.of(new ProductCacheEventDlqResult(
                    record.getId().getValue(),
                    Long.valueOf(String.valueOf(value.get(PRODUCT_ID))),
                    ProductCacheChangeType.valueOf(String.valueOf(value.get(CHANGE_TYPE))),
                    String.valueOf(value.getOrDefault(FAILURE_REASON, "")),
                    Instant.parse(String.valueOf(value.get(CREATED_AT)))
            ));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown error";
        }
        return value;
    }
}
