package com.product.application.port.in;

import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.dto.result.RebuildJobResult;

import java.util.UUID;

public interface ProductCacheAdminUseCase {
    RebuildJobResult rebuild(ProductCacheRebuildCommand command);
    RebuildJobResult getJob(UUID jobId);
}