package com.product.presentation.assembler;

import com.product.application.dto.result.ProductResult;
import com.product.presentation.dto.response.ProductResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductResponseAssembler {

    public ProductResponse from(ProductResult result) {
        if (result == null) {
            return null;
        }

        return new ProductResponse(
                result.id(),
                result.name(),
                result.price(),
                result.salePrice(),
                result.stock(),
                result.soldOut(),
                result.displayStatus(),
                result.updatedAt()
        );
    }
}