package com.product.application.service;

import com.product.application.cache.RebuildJob;
import com.product.application.cache.RebuildRequest;
import com.product.application.dto.command.ProductCacheChangeType;
import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.dto.result.ProductCacheEventDlqResult;
import com.product.application.dto.result.RebuildJobResult;
import com.product.application.port.in.ProductCacheEventUseCase;
import com.product.application.port.out.ProductCacheEventDlqPort;
import com.product.application.port.out.RebuildJobStore;
import com.product.domain.product.exception.ProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheRebuildServiceTest {

    @Mock
    private ProductCacheRebuildPlanner productCacheRebuildPlanner;

    @Mock
    private ProductCacheRebuildAsyncWorker productCacheRebuildAsyncWorker;

    @Mock
    private RebuildJobStore rebuildJobStore;

    @Mock
    private ProductCacheEventDlqPort productCacheEventDlqPort;

    @Mock
    private ProductCacheEventUseCase productCacheEventUseCase;

    @InjectMocks
    private ProductCacheRebuildService productCacheRebuildService;

    @Test
    @DisplayName("rebuild 는 planner 결과로 job 생성 후 async worker 를 호출하고 job 결과를 반환한다")
    void rebuild_shouldCreateJobAndInvokeAsyncWorker() {
        ProductCacheRebuildCommand command = new ProductCacheRebuildCommand(List.of(1L, 2L));
        RebuildRequest request = new RebuildRequest(List.of(1L, 2L), 500, "IDS(2)");
        RebuildJob job = RebuildJob.queued("IDS(2)", 2L);
        UUID jobId = job.getJobId();

        when(productCacheRebuildPlanner.plan(command)).thenReturn(request);
        when(rebuildJobStore.createIfAbsentActive("IDS(2)", 2L)).thenReturn(Optional.of(job));
        when(rebuildJobStore.find(jobId)).thenReturn(Optional.of(job));

        RebuildJobResult actual = productCacheRebuildService.rebuild(command);

        assertThat(actual.jobId()).isEqualTo(jobId);
        assertThat(actual.status()).isEqualTo("QUEUED");
        assertThat(actual.total()).isEqualTo(2L);
        assertThat(actual.processed()).isEqualTo(0L);
        assertThat(actual.progressPercent()).isEqualTo(0);
        assertThat(actual.active()).isTrue();
        assertThat(actual.failureReason()).isNull();
        assertThat(actual.filterSummary()).isEqualTo("IDS(2)");

        verify(productCacheRebuildPlanner).plan(command);
        verify(rebuildJobStore).createIfAbsentActive("IDS(2)", 2L);
        verify(productCacheRebuildAsyncWorker).rebuild(jobId, request);
        verify(rebuildJobStore).find(jobId);
        verify(rebuildJobStore, never()).markSucceeded(any(), any());
    }

    @Test
    @DisplayName("재빌드 대상이 없으면 성공 처리 후 worker 는 호출하지 않는다")
    void rebuild_whenRequestIsEmpty_thenMarkSucceededAndDoNotInvokeWorker() {
        ProductCacheRebuildCommand command = new ProductCacheRebuildCommand(List.of());
        RebuildRequest request = new RebuildRequest(List.of(), 500, "ALL");
        RebuildJob job = RebuildJob.queued("ALL", 0L);
        UUID jobId = job.getJobId();

        when(productCacheRebuildPlanner.plan(command)).thenReturn(request);
        when(rebuildJobStore.createIfAbsentActive("ALL", 0L)).thenReturn(Optional.of(job));
        doAnswer(invocation -> {
            job.markSucceeded(invocation.getArgument(1));
            return null;
        }).when(rebuildJobStore).markSucceeded(eq(jobId), anyString());
        when(rebuildJobStore.find(jobId)).thenReturn(Optional.of(job));

        RebuildJobResult actual = productCacheRebuildService.rebuild(command);

        assertThat(actual.jobId()).isEqualTo(jobId);
        assertThat(actual.status()).isEqualTo("SUCCEEDED");
        assertThat(actual.total()).isEqualTo(0L);
        assertThat(actual.processed()).isEqualTo(0L);
        assertThat(actual.progressPercent()).isEqualTo(100);
        assertThat(actual.active()).isFalse();
        assertThat(actual.failureReason()).isNull();
        assertThat(actual.filterSummary()).isEqualTo("ALL");

        InOrder inOrder = inOrder(productCacheRebuildPlanner, rebuildJobStore, productCacheRebuildAsyncWorker);
        inOrder.verify(productCacheRebuildPlanner).plan(command);
        inOrder.verify(rebuildJobStore).createIfAbsentActive("ALL", 0L);
        inOrder.verify(rebuildJobStore).markSucceeded(jobId, "재빌드 대상 상품이 없습니다.");

        verify(productCacheRebuildAsyncWorker, never()).rebuild(any(), any());
        verify(rebuildJobStore).find(jobId);
    }

    @Test
    @DisplayName("이미 진행 중인 재빌드 작업이 있으면 ProductException 이 발생한다")
    void rebuild_whenActiveJobAlreadyExists_thenThrowException() {
        ProductCacheRebuildCommand command = new ProductCacheRebuildCommand(List.of(1L, 2L));
        RebuildRequest request = new RebuildRequest(List.of(1L, 2L), 500, "IDS(2)");

        when(productCacheRebuildPlanner.plan(command)).thenReturn(request);
        when(rebuildJobStore.createIfAbsentActive("IDS(2)", 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productCacheRebuildService.rebuild(command))
                .isInstanceOf(ProductException.class);

        verify(productCacheRebuildPlanner).plan(command);
        verify(rebuildJobStore).createIfAbsentActive("IDS(2)", 2L);
        verify(productCacheRebuildAsyncWorker, never()).rebuild(any(), any());
    }

    @Test
    @DisplayName("getJob 은 저장소의 job 을 RebuildJobResult 로 변환한다")
    void getJob_shouldReturnMappedResult() {
        RebuildJob job = RebuildJob.queued("ALL", 4L);
        UUID jobId = job.getJobId();

        job.markRunning("start");
        job.updateProgress(2L, "청크 1/2 처리 완료");

        when(rebuildJobStore.find(jobId)).thenReturn(Optional.of(job));

        RebuildJobResult actual = productCacheRebuildService.getJob(jobId);

        assertThat(actual.jobId()).isEqualTo(jobId);
        assertThat(actual.status()).isEqualTo("RUNNING");
        assertThat(actual.total()).isEqualTo(4L);
        assertThat(actual.processed()).isEqualTo(2L);
        assertThat(actual.progressPercent()).isEqualTo(50);
        assertThat(actual.active()).isTrue();
        assertThat(actual.failureReason()).isNull();
        assertThat(actual.filterSummary()).isEqualTo("ALL");
        assertThat(actual.startedAt()).isNotNull();
        assertThat(actual.finishedAt()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 jobId 조회 시 ProductException 이 발생한다")
    void getJob_whenJobDoesNotExist_thenThrowException() {
        UUID jobId = UUID.randomUUID();

        when(rebuildJobStore.find(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productCacheRebuildService.getJob(jobId))
                .isInstanceOf(ProductException.class)
                .hasMessageContaining("존재하지 않는 jobId")
                .hasMessageContaining(jobId.toString());
    }

    @Test
    void getEventDlq_returnsDlqEvents() {
        ProductCacheEventDlqResult event = new ProductCacheEventDlqResult(
                "1-0",
                1L,
                ProductCacheChangeType.UPDATED,
                "failure",
                Instant.parse("2026-05-18T00:00:00Z")
        );
        when(productCacheEventDlqPort.findAll(10)).thenReturn(List.of(event));

        List<ProductCacheEventDlqResult> actual = productCacheRebuildService.getEventDlq(10);

        assertThat(actual).containsExactly(event);
    }

    @Test
    void reprocessEventDlq_whenEventExists_thenHandleEventAndDeleteFromDlq() {
        ProductCacheEventDlqResult event = new ProductCacheEventDlqResult(
                "1-0",
                1L,
                ProductCacheChangeType.DELETED,
                "failure",
                Instant.parse("2026-05-18T00:00:00Z")
        );
        when(productCacheEventDlqPort.find("1-0")).thenReturn(Optional.of(event));

        ProductCacheEventDlqResult actual = productCacheRebuildService.reprocessEventDlq("1-0");

        assertThat(actual).isEqualTo(event);
        verify(productCacheEventUseCase).handle(new ProductCacheChangedCommand(1L, ProductCacheChangeType.DELETED));
        verify(productCacheEventDlqPort).delete("1-0");
    }

    @Test
    void reprocessEventDlq_whenEventDoesNotExist_thenThrowException() {
        when(productCacheEventDlqPort.find("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productCacheRebuildService.reprocessEventDlq("missing"))
                .isInstanceOf(ProductException.class)
                .hasMessageContaining("존재하지 않는 DLQ 이벤트");
    }
}
