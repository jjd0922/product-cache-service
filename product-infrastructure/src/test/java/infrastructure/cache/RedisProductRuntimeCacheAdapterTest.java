package infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.application.common.exception.CacheOperationException;
import com.product.application.dto.cache.ProductRuntimeCacheData;
import com.product.infrastructure.cache.RedisProductRuntimeCacheAdapter;
import com.product.infrastructure.cache.support.ProductCacheCircuitBreaker;
import com.product.infrastructure.cache.support.ProductCacheKeyGenerator;
import com.product.infrastructure.cache.support.ProductCacheTtlPolicy;
import com.product.infrastructure.cache.support.RedisCacheBatchExecutor;
import com.product.infrastructure.config.ProductCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class RedisProductRuntimeCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ProductCacheProperties properties;

    @Mock
    private ProductCacheKeyGenerator keyGenerator;

    @Mock
    private RedisCacheBatchExecutor batchExecutor;

    @Mock
    private ProductCacheTtlPolicy ttlPolicy;

    @Mock
    private ProductCacheCircuitBreaker circuitBreaker;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisProductRuntimeCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisProductRuntimeCacheAdapter(
                redisTemplate,
                objectMapper,
                properties,
                keyGenerator,
                batchExecutor,
                ttlPolicy,
                circuitBreaker
        );
        lenient().when(circuitBreaker.executeSupplier(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get()
        );
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(circuitBreaker).executeRunnable(any());
    }

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("상품 ID 가 null 이면 empty 를 반환한다")
        void returnsEmpty_whenProductIdIsNull() {
            Optional<ProductRuntimeCacheData> actual = adapter.get(null);

            assertThat(actual).isEmpty();
            verify(valueOperations, never()).get(anyString());
            verifyNoInteractions(objectMapper);
        }

        @Test
        @DisplayName("상품 ID 가 0 이하이면 empty 를 반환한다")
        void returnsEmpty_whenProductIdIsInvalid() {
            Optional<ProductRuntimeCacheData> actual = adapter.get(0L);

            assertThat(actual).isEmpty();
            verify(valueOperations, never()).get(anyString());
            verifyNoInteractions(objectMapper);
        }

        @Test
        @DisplayName("캐시에 값이 없으면 empty 를 반환한다")
        void returnsEmpty_whenCacheMiss() {
            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.get("product:runtime:1")).thenReturn(null);

            Optional<ProductRuntimeCacheData> actual = adapter.get(1L);

            assertThat(actual).isEmpty();
            verify(valueOperations).get("product:runtime:1");
        }

        @Test
        @DisplayName("캐시에 값이 있으면 역직렬화하여 반환한다")
        void returnsRuntimeData_whenCacheHit() throws Exception {
            ProductRuntimeCacheData expected = runtimeData(1L);

            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.get("product:runtime:1")).thenReturn("{json}");
            when(objectMapper.readValue("{json}", ProductRuntimeCacheData.class)).thenReturn(expected);

            Optional<ProductRuntimeCacheData> actual = adapter.get(1L);

            assertThat(actual).contains(expected);
            verify(valueOperations).get("product:runtime:1");
            verify(objectMapper).readValue("{json}", ProductRuntimeCacheData.class);
        }

        @Test
        @DisplayName("payload productId 가 요청 id 와 다르면 캐시를 삭제하고 empty 를 반환한다")
        void returnsEmptyAndDeleteCache_whenPayloadIdMismatch() throws Exception {
            ProductRuntimeCacheData mismatched = runtimeData(99L);

            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.get("product:runtime:1")).thenReturn("{json}");
            when(objectMapper.readValue("{json}", ProductRuntimeCacheData.class)).thenReturn(mismatched);

            Optional<ProductRuntimeCacheData> actual = adapter.get(1L);

            assertThat(actual).isEmpty();
            verify(redisTemplate).delete("product:runtime:1");
        }

        @Test
        @DisplayName("Redis 조회 예외가 발생하면 empty 를 반환한다")
        void returnsEmpty_whenRedisReadFails() {
            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.get("product:runtime:1"))
                    .thenThrow(new DataAccessResourceFailureException("redis down"));

            Optional<ProductRuntimeCacheData> actual = adapter.get(1L);

            assertThat(actual).isEmpty();
            verify(redisTemplate, never()).delete("product:runtime:1");
        }

        @Test
        @DisplayName("역직렬화 실패 시 캐시를 삭제하고 empty 를 반환한다")
        void returnsEmptyAndDeleteCache_whenDeserializationFails() throws Exception {
            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.get("product:runtime:1")).thenReturn("{broken}");
            when(objectMapper.readValue("{broken}", ProductRuntimeCacheData.class))
                    .thenThrow(new JsonProcessingException("broken json") {});

            Optional<ProductRuntimeCacheData> actual = adapter.get(1L);

            assertThat(actual).isEmpty();
            verify(redisTemplate).delete("product:runtime:1");
        }

        @Test
        @DisplayName("조회 실패 후 캐시 삭제까지 실패해도 예외를 던지지 않는다")
        void returnsEmpty_whenDeleteAlsoFailsAfterReadFailure() throws Exception {
            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.get("product:runtime:1")).thenReturn("{broken}");
            when(objectMapper.readValue("{broken}", ProductRuntimeCacheData.class))
                    .thenThrow(new JsonProcessingException("broken json") {});
            when(redisTemplate.delete("product:runtime:1"))
                    .thenThrow(new DataAccessResourceFailureException("delete fail"));

            Optional<ProductRuntimeCacheData> actual = adapter.get(1L);

            assertThat(actual).isEmpty();
            verify(redisTemplate).delete("product:runtime:1");
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("입력이 null 이면 빈 Map 을 반환한다")
        void returnsEmptyMap_whenIdsAreNull() {
            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(null);

            assertThat(actual).isEmpty();
            verify(valueOperations, never()).multiGet(anyList());
        }

        @Test
        @DisplayName("유효한 ID 가 없으면 빈 Map 을 반환한다")
        void returnsEmptyMap_whenNoValidIds() {
            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(Arrays.asList(null, 0L, -1L));

            assertThat(actual).isEmpty();
            verify(valueOperations, never()).multiGet(anyList());
        }

        @Test
        @DisplayName("중복과 무효 ID 를 제거한 key 목록으로 multiGet 한다")
        void callsMultiGetWithNormalizedKeys() {
            stubValueOps();
            stubRuntimeKeys(1L, 2L);
            when(valueOperations.multiGet(List.of("product:runtime:1", "product:runtime:2")))
                    .thenReturn(Arrays.asList(null, null));

            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(Arrays.asList(1L, 2L, 1L, null, -1L));

            assertThat(actual).isEmpty();
            verify(valueOperations).multiGet(List.of("product:runtime:1", "product:runtime:2"));
        }

        @Test
        @DisplayName("역직렬화 가능하고 요청 id 와 일치하는 값만 결과에 포함한다")
        void returnsOnlyDeserializableAndMatchedValues() throws Exception {
            ProductRuntimeCacheData data1 = runtimeData(1L);

            stubValueOps();
            stubRuntimeKeys(1L, 2L);
            when(valueOperations.multiGet(List.of("product:runtime:1", "product:runtime:2")))
                    .thenReturn(List.of("{json1}", "{broken}"));
            when(objectMapper.readValue("{json1}", ProductRuntimeCacheData.class)).thenReturn(data1);
            when(objectMapper.readValue("{broken}", ProductRuntimeCacheData.class))
                    .thenThrow(new JsonProcessingException("broken json") {});

            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(List.of(1L, 2L));

            assertThat(actual).containsEntry(1L, data1);
            assertThat(actual).hasSize(1);
            verify(redisTemplate).delete("product:runtime:2");
        }

        @Test
        @DisplayName("역직렬화 결과의 productId 가 요청 id 와 다르면 삭제 후 결과에서 제외한다")
        void excludesResultAndDelete_whenDeserializedProductIdMismatch() throws Exception {
            ProductRuntimeCacheData mismatched = runtimeData(99L);

            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.multiGet(List.of("product:runtime:1")))
                    .thenReturn(List.of("{json1}"));
            when(objectMapper.readValue("{json1}", ProductRuntimeCacheData.class)).thenReturn(mismatched);

            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(List.of(1L));

            assertThat(actual).isEmpty();
            verify(redisTemplate).delete("product:runtime:1");
        }

        @Test
        @DisplayName("역직렬화 결과의 productId 가 null 이면 삭제 후 결과에서 제외한다")
        void excludesResultAndDelete_whenDeserializedProductIdIsNull() throws Exception {
            ProductRuntimeCacheData invalid = runtimeData(null);

            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.multiGet(List.of("product:runtime:1")))
                    .thenReturn(List.of("{json1}"));
            when(objectMapper.readValue("{json1}", ProductRuntimeCacheData.class)).thenReturn(invalid);

            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(List.of(1L));

            assertThat(actual).isEmpty();
            verify(redisTemplate).delete("product:runtime:1");
        }

        @Test
        @DisplayName("multiGet 결과가 null 이면 빈 Map 을 반환한다")
        void returnsEmptyMap_whenMultiGetReturnsNull() {
            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.multiGet(List.of("product:runtime:1"))).thenReturn(null);

            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(List.of(1L));

            assertThat(actual).isEmpty();
        }

        @Test
        @DisplayName("Redis 일괄 조회 예외가 발생하면 빈 Map 을 반환한다")
        void returnsEmptyMap_whenRedisReadFails() {
            stubValueOps();
            stubRuntimeKey(1L);
            when(valueOperations.multiGet(List.of("product:runtime:1")))
                    .thenThrow(new DataAccessResourceFailureException("redis down"));

            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(List.of(1L));

            assertThat(actual).isEmpty();
        }

        @Test
        @DisplayName("항목 삭제까지 실패해도 나머지 정상 데이터는 반환한다")
        void returnsRemainingRuntimeData_whenDeleteAlsoFailsAfterEntryReadFailure() throws Exception {
            ProductRuntimeCacheData data1 = runtimeData(1L);

            stubValueOps();
            stubRuntimeKeys(1L, 2L);
            when(valueOperations.multiGet(List.of("product:runtime:1", "product:runtime:2")))
                    .thenReturn(List.of("{json1}", "{broken}"));
            when(objectMapper.readValue("{json1}", ProductRuntimeCacheData.class)).thenReturn(data1);
            when(objectMapper.readValue("{broken}", ProductRuntimeCacheData.class))
                    .thenThrow(new JsonProcessingException("broken json") {});
            when(redisTemplate.delete("product:runtime:2"))
                    .thenThrow(new DataAccessResourceFailureException("delete fail"));

            Map<Long, ProductRuntimeCacheData> actual = adapter.getAll(List.of(1L, 2L));

            assertThat(actual).containsEntry(1L, data1);
            assertThat(actual).hasSize(1);
            verify(redisTemplate).delete("product:runtime:2");
        }
    }

    @Nested
    @DisplayName("put")
    class Put {

        @Test
        @DisplayName("runtime data 가 null 이면 저장하지 않는다")
        void doNothing_whenRuntimeDataIsNull() {
            adapter.put(null);

            verifyNoInteractions(objectMapper);
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("productId 가 null 이면 저장하지 않는다")
        void doNothing_whenProductIdIsNull() {
            adapter.put(runtimeData(null));

            verifyNoInteractions(objectMapper);
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("정상 데이터면 TTL 과 함께 저장한다")
        void saveWithTtl_whenRuntimeDataIsValid() throws Exception {
            ProductRuntimeCacheData data = runtimeData(1L);

            stubValueOps();
            stubRuntimeKey(1L);
            stubRuntimeTtl(300L);
            when(objectMapper.writeValueAsString(data)).thenReturn("{json}");

            adapter.put(data);

            verify(valueOperations).set(
                    eq("product:runtime:1"),
                    eq("{json}"),
                    eq(Duration.ofSeconds(300L))
            );
        }

        @Test
        @DisplayName("직렬화 실패 시 CacheOperationException 이 발생한다")
        void throwsCacheOperationException_whenSerializationFails() throws Exception {
            ProductRuntimeCacheData data = runtimeData(1L);

            stubRuntimeKey(1L);
            when(objectMapper.writeValueAsString(data))
                    .thenThrow(new JsonProcessingException("serialize fail") {});

            assertThatThrownBy(() -> adapter.put(data))
                    .isInstanceOf(CacheOperationException.class)
                    .hasMessageContaining("상품 runtime cache 직렬화 실패");

            verify(objectMapper).writeValueAsString(data);
            verify(redisTemplate, never()).opsForValue();
            verifyNoInteractions(batchExecutor);
        }

        @Test
        @DisplayName("Redis 저장 실패 시 CacheOperationException 이 발생한다")
        void throwsCacheOperationException_whenRedisSetFails() throws Exception {
            ProductRuntimeCacheData data = runtimeData(1L);

            stubValueOps();
            stubRuntimeKey(1L);
            stubRuntimeTtl(300L);
            when(objectMapper.writeValueAsString(data)).thenReturn("{json}");
            doThrow(new DataAccessResourceFailureException("redis down"))
                    .when(valueOperations)
                    .set(eq("product:runtime:1"), eq("{json}"), eq(Duration.ofSeconds(300L)));

            assertThatThrownBy(() -> adapter.put(data))
                    .isInstanceOf(CacheOperationException.class)
                    .hasMessageContaining("상품 runtime cache 저장 실패");
        }
    }

    @Nested
    @DisplayName("putAll")
    class PutAll {

        @Test
        @DisplayName("입력이 null 이면 아무 작업도 하지 않는다")
        void doNothing_whenRuntimeDataListIsNull() {
            adapter.putAll(null);

            verify(batchExecutor, never()).setExBatch(anyList(), anyLong());
        }

        @Test
        @DisplayName("유효한 데이터가 없으면 아무 작업도 하지 않는다")
        void doNothing_whenNoValidRuntimeData() {
            adapter.putAll(Arrays.asList(null, runtimeData(null)));

            verify(batchExecutor, never()).setExBatch(anyList(), anyLong());
        }

        @Test
        @DisplayName("pipeline batch size 가 1 미만이면 예외가 발생한다")
        void throwsException_whenBatchSizeIsInvalid() {
            stubBatchSize(0);

            assertThatThrownBy(() -> adapter.putAll(List.of(runtimeData(1L))))
                    .isInstanceOf(CacheOperationException.class)
                    .hasMessageContaining("pipelineBatchSize 는 1 이상이어야 합니다");
        }

        @Test
        @DisplayName("정상 데이터면 batchExecutor 로 TTL 과 함께 일괄 저장한다")
        void saveAllWithBatchExecutor_whenRuntimeDataIsValid() throws Exception {
            ProductRuntimeCacheData data1 = runtimeData(1L);
            ProductRuntimeCacheData data2 = runtimeData(2L);

            stubBatchSize(100);
            stubRuntimeTtl(300L);
            stubRuntimeKeys(1L, 2L);
            when(objectMapper.writeValueAsString(data1)).thenReturn("{json1}");
            when(objectMapper.writeValueAsString(data2)).thenReturn("{json2}");

            adapter.putAll(List.of(data1, data2));

            verify(batchExecutor).setExBatch(anyList(), eq(300L));
            verify(keyGenerator).runtimeKey(1L);
            verify(keyGenerator).runtimeKey(2L);
        }

        @Test
        @DisplayName("배치 크기를 초과하면 여러 번 나누어 저장한다")
        void splitIntoMultipleBatches_whenDataCountExceedsBatchSize() throws Exception {
            ProductRuntimeCacheData data1 = runtimeData(1L);
            ProductRuntimeCacheData data2 = runtimeData(2L);
            ProductRuntimeCacheData data3 = runtimeData(3L);
            ProductRuntimeCacheData data4 = runtimeData(4L);
            ProductRuntimeCacheData data5 = runtimeData(5L);

            stubBatchSize(2);
            stubRuntimeTtl(300L);
            stubRuntimeKeys(1L, 2L, 3L, 4L, 5L);
            when(objectMapper.writeValueAsString(data1)).thenReturn("{json1}");
            when(objectMapper.writeValueAsString(data2)).thenReturn("{json2}");
            when(objectMapper.writeValueAsString(data3)).thenReturn("{json3}");
            when(objectMapper.writeValueAsString(data4)).thenReturn("{json4}");
            when(objectMapper.writeValueAsString(data5)).thenReturn("{json5}");

            adapter.putAll(List.of(data1, data2, data3, data4, data5));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<RedisCacheBatchExecutor.CacheEntry>> captor =
                    ArgumentCaptor.forClass((Class<List<RedisCacheBatchExecutor.CacheEntry>>) (Class<?>) List.class);

            verify(batchExecutor, times(3)).setExBatch(captor.capture(), eq(300L));
            assertThat(captor.getAllValues()).hasSize(3);
            assertThat(captor.getAllValues().get(0)).hasSize(2);
            assertThat(captor.getAllValues().get(1)).hasSize(2);
            assertThat(captor.getAllValues().get(2)).hasSize(1);
        }

        @Test
        @DisplayName("배치 직렬화 중 하나라도 실패하면 CacheOperationException 이 발생한다")
        void throwsException_whenAnySerializationFailsInBatch() throws Exception {
            ProductRuntimeCacheData data1 = runtimeData(1L);
            ProductRuntimeCacheData data2 = runtimeData(2L);

            stubBatchSize(100);
            stubRuntimeKeys(1L, 2L);
            when(objectMapper.writeValueAsString(data1)).thenReturn("{json1}");
            when(objectMapper.writeValueAsString(data2))
                    .thenThrow(new JsonProcessingException("serialize fail") {});

            assertThatThrownBy(() -> adapter.putAll(List.of(data1, data2)))
                    .isInstanceOf(CacheOperationException.class)
                    .hasMessageContaining("상품 runtime cache 배치 직렬화 실패");

            verify(batchExecutor, never()).setExBatch(anyList(), anyLong());
        }

        @Test
        @DisplayName("batch 저장 실패 시 CacheOperationException 이 발생한다")
        void throwsException_whenBatchExecutionFails() throws Exception {
            ProductRuntimeCacheData data1 = runtimeData(1L);
            ProductRuntimeCacheData data2 = runtimeData(2L);

            stubBatchSize(100);
            stubRuntimeTtl(300L);
            stubRuntimeKeys(1L, 2L);
            when(objectMapper.writeValueAsString(data1)).thenReturn("{json1}");
            when(objectMapper.writeValueAsString(data2)).thenReturn("{json2}");
            doThrow(new RuntimeException("redis down"))
                    .when(batchExecutor).setExBatch(anyList(), eq(300L));

            assertThatThrownBy(() -> adapter.putAll(List.of(data1, data2)))
                    .isInstanceOf(CacheOperationException.class)
                    .hasMessageContaining("상품 runtime cache pipeline 저장 실패");
        }
    }

    @Nested
    @DisplayName("evict")
    class Evict {

        @Test
        @DisplayName("상품 ID 가 null 이면 삭제하지 않는다")
        void doNothing_whenProductIdIsNull() {
            adapter.evict(null);

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("상품 ID 가 0 이하이면 삭제하지 않는다")
        void doNothing_whenProductIdIsInvalid() {
            adapter.evict(0L);

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("유효한 상품 ID 면 key 로 삭제한다")
        void deleteByKey_whenProductIdIsValid() {
            stubRuntimeKey(1L);

            adapter.evict(1L);

            verify(redisTemplate).delete("product:runtime:1");
        }

        @Test
        @DisplayName("삭제 실패 시 CacheOperationException 이 발생한다")
        void throwsException_whenDeleteFails() {
            stubRuntimeKey(1L);
            doThrow(new DataAccessResourceFailureException("redis down"))
                    .when(redisTemplate).delete("product:runtime:1");

            assertThatThrownBy(() -> adapter.evict(1L))
                    .isInstanceOf(CacheOperationException.class)
                    .hasMessageContaining("상품 runtime cache 삭제 실패");
        }
    }

    @Nested
    @DisplayName("evictAll")
    class EvictAll {

        @Test
        @DisplayName("입력이 null 이면 아무 작업도 하지 않는다")
        void doNothing_whenIdsAreNull() {
            adapter.evictAll(null);

            verify(redisTemplate, never()).delete(anyCollection());
        }

        @Test
        @DisplayName("유효한 ID 가 없으면 아무 작업도 하지 않는다")
        void doNothing_whenNoValidIds() {
            adapter.evictAll(Arrays.asList(null, 0L, -1L));

            verify(redisTemplate, never()).delete(anyCollection());
        }

        @Test
        @DisplayName("중복 제거 후 key 목록으로 삭제한다")
        void deleteAllByKeys_whenIdsAreValid() {
            stubRuntimeKeys(1L, 2L);

            adapter.evictAll(Arrays.asList(1L, null, 2L, 1L, -1L));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> captor =
                    ArgumentCaptor.forClass((Class<Collection<String>>) (Class<?>) Collection.class);

            verify(redisTemplate).delete(captor.capture());
            assertThat(captor.getValue()).containsExactly("product:runtime:1", "product:runtime:2");
        }

        @Test
        @DisplayName("일괄 삭제 실패 시 CacheOperationException 이 발생한다")
        void throwsException_whenDeleteAllFails() {
            stubRuntimeKeys(1L, 2L);
            doThrow(new DataAccessResourceFailureException("redis down"))
                    .when(redisTemplate).delete(List.of("product:runtime:1", "product:runtime:2"));

            assertThatThrownBy(() -> adapter.evictAll(List.of(1L, 2L)))
                    .isInstanceOf(CacheOperationException.class)
                    .hasMessageContaining("상품 runtime cache 일괄 삭제 실패");
        }
    }

    private void stubValueOps() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void stubRuntimeTtl(long ttlSeconds) {
        when(ttlPolicy.runtimeTtlSeconds()).thenReturn(ttlSeconds);
    }

    private void stubBatchSize(int batchSize) {
        when(properties.getPipelineBatchSize()).thenReturn(batchSize);
    }

    private void stubRuntimeKey(Long productId) {
        when(keyGenerator.runtimeKey(productId)).thenReturn("product:runtime:" + productId);
    }

    private void stubRuntimeKeys(Long... productIds) {
        for (Long productId : productIds) {
            stubRuntimeKey(productId);
        }
    }

    private ProductRuntimeCacheData runtimeData(Long productId) {
        return new ProductRuntimeCacheData(
                productId,
                new BigDecimal("9000"),
                10,
                false,
                "ON_SALE",
                Instant.parse("2026-03-20T00:00:00Z")
        );
    }
}
