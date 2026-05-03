package com.product.presentation.controller;

import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.dto.result.RebuildJobResult;
import com.product.application.port.in.ProductCacheAdminUseCase;
import com.product.presentation.assembler.ProductCacheAdminRequestAssembler;
import com.product.presentation.assembler.ProductCacheAdminResponseAssembler;
import com.product.presentation.common.response.ApiResponse;
import com.product.presentation.dto.request.RebuildRequest;
import com.product.presentation.dto.response.CacheJobStatusResponse;
import com.product.presentation.dto.response.RebuildStartedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/admin/cache/products")
@RequiredArgsConstructor
public class ProductCacheAdminController {

    private final ProductCacheAdminUseCase productCacheAdminUseCase;
    private final ProductCacheAdminRequestAssembler productCacheAdminRequestAssembler;
    private final ProductCacheAdminResponseAssembler productCacheAdminResponseAssembler;

    @PostMapping("/rebuild")
    public ResponseEntity<ApiResponse<RebuildStartedResponse>> rebuild(@RequestBody(required = false) RebuildRequest request) {
        ProductCacheRebuildCommand command = productCacheAdminRequestAssembler.from(request);
        RebuildJobResult result = productCacheAdminUseCase.rebuild(command);
        RebuildStartedResponse response = productCacheAdminResponseAssembler.toStartedResponse(result);
        URI location = URI.create("/admin/cache/products/jobs/" + result.jobId());

        return ResponseEntity.accepted()
                .location(location)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<CacheJobStatusResponse>> getJob(@PathVariable UUID jobId) {
        RebuildJobResult result = productCacheAdminUseCase.getJob(jobId);
        CacheJobStatusResponse response = productCacheAdminResponseAssembler.toStatusResponse(result);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ApiResponse.success(response));
    }
}
