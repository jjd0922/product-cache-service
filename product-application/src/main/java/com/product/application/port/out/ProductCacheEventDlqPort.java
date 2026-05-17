package com.product.application.port.out;

import com.product.application.dto.command.ProductCacheChangedCommand;
import com.product.application.dto.result.ProductCacheEventDlqResult;

import java.util.List;
import java.util.Optional;

public interface ProductCacheEventDlqPort {

    void append(ProductCacheChangedCommand command, String failureReason);

    List<ProductCacheEventDlqResult> findAll(int limit);

    Optional<ProductCacheEventDlqResult> find(String eventId);

    void delete(String eventId);
}
