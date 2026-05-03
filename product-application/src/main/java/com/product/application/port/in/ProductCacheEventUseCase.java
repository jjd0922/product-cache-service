package com.product.application.port.in;

import com.product.application.dto.command.ProductCacheChangedCommand;

public interface ProductCacheEventUseCase {

    void handle(ProductCacheChangedCommand command);
}
