package infrastructure.event;

import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.dto.result.ProductCacheEventDlqResult;
import com.product.infrastructure.config.ProductCacheProperties;
import com.product.infrastructure.event.RedisProductCacheEventDlqAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisProductCacheEventDlqAdapterTest {

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private ProductCacheProperties properties;
    private RedisProductCacheEventDlqAdapter adapter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        properties = new ProductCacheProperties();
        properties.setEventDlqStreamKey("product:cache:event:dlq");
        adapter = new RedisProductCacheEventDlqAdapter(redisTemplate, properties);

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Test
    void append_writesEventToRedisStream() {
        adapter.append(new ProductCacheChangedCommand(1L, ProductCacheChangeType.UPDATED), "redis down");

        verify(streamOperations).add(argThat(record ->
                "product:cache:event:dlq".equals(record.getStream())
                        && "1".equals(record.getValue().get("productId"))
                        && "UPDATED".equals(record.getValue().get("changeType"))
                        && "redis down".equals(record.getValue().get("failureReason"))
        ));
    }

    @Test
    void find_returnsEventById() {
        Map<Object, Object> body = Map.of(
                "productId", "1",
                "changeType", "DELETED",
                "failureReason", "permanent failure",
                "createdAt", "2026-05-18T00:00:00Z"
        );
        MapRecord<String, Object, Object> record = MapRecord.create(
                "product:cache:event:dlq",
                body
        ).withId(RecordId.of("1-0"));

        when(streamOperations.range(eq("product:cache:event:dlq"), any()))
                .thenReturn(List.of(record));

        Optional<ProductCacheEventDlqResult> actual = adapter.find("1-0");

        assertThat(actual).isPresent();
        assertThat(actual.get().eventId()).isEqualTo("1-0");
        assertThat(actual.get().productId()).isEqualTo(1L);
        assertThat(actual.get().changeType()).isEqualTo(ProductCacheChangeType.DELETED);
    }

    @Test
    void delete_removesEventFromStream() {
        adapter.delete("1-0");

        verify(streamOperations).delete("product:cache:event:dlq", RecordId.of("1-0"));
    }
}
