package com.product.presentation.controller;

import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.dto.result.RebuildJobResult;
import com.product.application.port.in.ProductCacheAdminUseCase;
import com.product.presentation.assembler.ProductCacheAdminRequestAssembler;
import com.product.presentation.assembler.ProductCacheAdminResponseAssembler;
import com.product.presentation.common.response.ApiResponse;
import com.product.presentation.controller.ProductCacheAdminController;
import com.product.presentation.dto.request.RebuildRequest;
import com.product.presentation.dto.response.CacheJobStatusResponse;
import com.product.presentation.dto.response.RebuildStartedResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheAdminControllerTest {

    @Mock
    private ProductCacheAdminUseCase productCacheAdminUseCase;

    @Mock
    private ProductCacheAdminRequestAssembler productCacheAdminRequestAssembler;

    @Mock
    private ProductCacheAdminResponseAssembler productCacheAdminResponseAssembler;

    @InjectMocks
    private ProductCacheAdminController productCacheAdminController;

    @Test
    @DisplayName("rebuild 는 request 를 command 로 변환하고 재빌드 시작 응답을 반환한다")
    void rebuild_whenRequestExists_thenReturnStartedResponse() {
        RebuildRequest request = mock(RebuildRequest.class);
        ProductCacheRebuildCommand command = new ProductCacheRebuildCommand(List.of(1L, 2L));

        UUID jobId = UUID.randomUUID();
        RebuildJobResult result = new RebuildJobResult(
                jobId,
                "RUNNING",
                2L,
                0L,
                0,
                true,
                "재빌드를 시작했습니다.",
                null,
                "IDS(2)",
                LocalDateTime.of(2026, 3, 20, 0, 0, 0),
                null
        );

        RebuildStartedResponse response = mock(RebuildStartedResponse.class);

        when(productCacheAdminRequestAssembler.from(request)).thenReturn(command);
        when(productCacheAdminUseCase.rebuild(command)).thenReturn(result);
        when(productCacheAdminResponseAssembler.toStartedResponse(result)).thenReturn(response);

        ResponseEntity<ApiResponse<RebuildStartedResponse>> actual =
                productCacheAdminController.rebuild(request);

        assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(actual.getHeaders().getLocation()).hasToString("/admin/cache/products/jobs/" + jobId);
        assertThat(actual.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(actual.getBody()).isNotNull();
        assertThat(actual.getBody().success()).isTrue();
        assertThat(actual.getBody().data()).isEqualTo(response);

        verify(productCacheAdminRequestAssembler).from(request);
        verify(productCacheAdminUseCase).rebuild(command);
        verify(productCacheAdminResponseAssembler).toStartedResponse(result);
        verifyNoMoreInteractions(
                productCacheAdminRequestAssembler,
                productCacheAdminUseCase,
                productCacheAdminResponseAssembler
        );
    }

    @Test
    @DisplayName("rebuild 는 request 가 null 이어도 assembler 에 null 을 전달한다")
    void rebuild_whenRequestIsNull_thenPassNullToAssembler() {
        ProductCacheRebuildCommand command = new ProductCacheRebuildCommand(List.of());

        UUID jobId = UUID.randomUUID();
        RebuildJobResult result = new RebuildJobResult(
                jobId,
                "SUCCEEDED",
                0L,
                0L,
                100,
                false,
                "재빌드 대상 상품이 없습니다.",
                null,
                "ALL",
                LocalDateTime.of(2026, 3, 20, 0, 0, 0),
                LocalDateTime.of(2026, 3, 20, 0, 0, 1)
        );

        RebuildStartedResponse response = mock(RebuildStartedResponse.class);

        when(productCacheAdminRequestAssembler.from(null)).thenReturn(command);
        when(productCacheAdminUseCase.rebuild(command)).thenReturn(result);
        when(productCacheAdminResponseAssembler.toStartedResponse(result)).thenReturn(response);

        ResponseEntity<ApiResponse<RebuildStartedResponse>> actual =
                productCacheAdminController.rebuild(null);

        assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(actual.getHeaders().getLocation()).hasToString("/admin/cache/products/jobs/" + jobId);
        assertThat(actual.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(actual.getBody()).isNotNull();
        assertThat(actual.getBody().success()).isTrue();
        assertThat(actual.getBody().data()).isEqualTo(response);

        verify(productCacheAdminRequestAssembler).from(null);
        verify(productCacheAdminUseCase).rebuild(command);
        verify(productCacheAdminResponseAssembler).toStartedResponse(result);
        verifyNoMoreInteractions(
                productCacheAdminRequestAssembler,
                productCacheAdminUseCase,
                productCacheAdminResponseAssembler
        );
    }

    @Test
    @DisplayName("getJob 은 jobId 로 조회 후 상태 응답을 반환한다")
    void getJob_whenJobExists_thenReturnStatusResponse() {
        UUID jobId = UUID.randomUUID();

        RebuildJobResult result = new RebuildJobResult(
                jobId,
                "RUNNING",
                10L,
                3L,
                30,
                true,
                "재빌드 진행 중입니다.",
                null,
                "ALL",
                LocalDateTime.of(2026, 3, 20, 0, 0, 0),
                null
        );

        CacheJobStatusResponse response = mock(CacheJobStatusResponse.class);

        when(productCacheAdminUseCase.getJob(jobId)).thenReturn(result);
        when(productCacheAdminResponseAssembler.toStatusResponse(result)).thenReturn(response);

        ResponseEntity<ApiResponse<CacheJobStatusResponse>> actual =
                productCacheAdminController.getJob(jobId);

        assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actual.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(actual.getHeaders().getPragma()).isEqualTo("no-cache");
        assertThat(actual.getBody()).isNotNull();
        assertThat(actual.getBody().success()).isTrue();
        assertThat(actual.getBody().data()).isEqualTo(response);

        verify(productCacheAdminUseCase).getJob(jobId);
        verify(productCacheAdminResponseAssembler).toStatusResponse(result);
        verifyNoMoreInteractions(
                productCacheAdminUseCase,
                productCacheAdminResponseAssembler
        );
        verifyNoInteractions(productCacheAdminRequestAssembler);
    }
}
