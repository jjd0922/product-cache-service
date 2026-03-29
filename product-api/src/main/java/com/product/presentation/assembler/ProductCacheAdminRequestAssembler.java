package com.product.presentation.assembler;

import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.presentation.dto.request.RebuildRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductCacheAdminRequestAssembler {

    public ProductCacheRebuildCommand from(RebuildRequest request) {
        List<Long> productIds = request != null && request.productIds() != null
                ? request.productIds()
                : List.of();

        return new ProductCacheRebuildCommand(productIds);
    }
}