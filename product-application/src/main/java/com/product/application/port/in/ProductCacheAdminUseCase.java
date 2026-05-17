package com.product.application.port.in;

import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.dto.result.ProductCacheEventDlqResult;
import com.product.application.dto.result.RebuildJobResult;

import java.util.List;
import java.util.UUID;

public interface ProductCacheAdminUseCase {
    RebuildJobResult rebuild(ProductCacheRebuildCommand command);
    RebuildJobResult getJob(UUID jobId);
    List<ProductCacheEventDlqResult> getEventDlq(int limit);
    ProductCacheEventDlqResult reprocessEventDlq(String eventId);
}
