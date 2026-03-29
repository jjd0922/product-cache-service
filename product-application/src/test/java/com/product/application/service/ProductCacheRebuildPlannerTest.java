package com.product.application.service;

import com.product.application.cache.RebuildRequest;
import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.port.out.ProductReadPort;
import com.product.domain.product.exception.ProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheRebuildPlannerTest {

    @Mock
    private ProductReadPort productReadPort;

    @InjectMocks
    private ProductCacheRebuildPlanner productCacheRebuildPlanner;

    @Test
    @DisplayName("command 가 null 이면 전체 상품 ID 기준 재빌드 요청을 반환한다")
    void plan_whenCommandIsNull_thenReturnAllIdsRequest() {
        when(productReadPort.findAllIds()).thenReturn(List.of(1L, 2L));

        RebuildRequest actual = productCacheRebuildPlanner.plan(null);

        assertThat(actual.targetProductIds()).containsExactly(1L, 2L);
        assertThat(actual.chunkSize()).isEqualTo(500);
        assertThat(actual.filterSummary()).isEqualTo("ALL");

        verify(productReadPort).findAllIds();
    }

    @Test
    @DisplayName("productIds 가 비어있으면 전체 상품 ID 기준 재빌드 요청을 반환한다")
    void plan_whenProductIdsEmpty_thenReturnAllIdsRequest() {
        when(productReadPort.findAllIds()).thenReturn(List.of(10L, 20L));

        RebuildRequest actual = productCacheRebuildPlanner.plan(new ProductCacheRebuildCommand(List.of()));

        assertThat(actual.targetProductIds()).containsExactly(10L, 20L);
        assertThat(actual.chunkSize()).isEqualTo(500);
        assertThat(actual.filterSummary()).isEqualTo("ALL");

        verify(productReadPort).findAllIds();
    }

    @Test
    @DisplayName("유효한 상품 ID 만 중복 제거 후 순서를 유지하여 재빌드 요청을 만든다")
    void plan_whenIdsExist_thenReturnNormalizedRequest() {
        RebuildRequest actual = productCacheRebuildPlanner.plan(
                new ProductCacheRebuildCommand(java.util.Arrays.asList(1L, 2L, 2L, null, -1L, 0L, 1L))
        );

        assertThat(actual.targetProductIds()).containsExactly(1L, 2L);
        assertThat(actual.chunkSize()).isEqualTo(500);
        assertThat(actual.filterSummary()).isEqualTo("IDS(2)");

        verify(productReadPort, never()).findAllIds();
    }

    @Test
    @DisplayName("유효한 ID 가 하나도 없어도 빈 재빌드 요청과 IDS(0) summary 를 반환한다")
    void plan_whenNoValidIds_thenReturnEmptyRequest() {
        RebuildRequest actual = productCacheRebuildPlanner.plan(
                new ProductCacheRebuildCommand(java.util.Arrays.asList(null, -1L, 0L))
        );

        assertThat(actual.targetProductIds()).isEmpty();
        assertThat(actual.chunkSize()).isEqualTo(500);
        assertThat(actual.filterSummary()).isEqualTo("IDS(0)");
        assertThat(actual.isEmpty()).isTrue();

        verify(productReadPort, never()).findAllIds();
    }

    @Test
    @DisplayName("재빌드 대상 수가 제한을 초과하면 ProductException 이 발생한다")
    void plan_whenTargetCountExceedsLimit_thenThrowException() {
        List<Long> ids = LongStream.rangeClosed(1, 30_001)
                .boxed()
                .collect(Collectors.toList());

        assertThatThrownBy(() -> productCacheRebuildPlanner.plan(new ProductCacheRebuildCommand(ids)))
                .isInstanceOf(ProductException.class);

        verify(productReadPort, never()).findAllIds();
    }
}