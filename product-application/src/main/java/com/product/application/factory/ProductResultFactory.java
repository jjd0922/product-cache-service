package com.product.application.factory;

import com.product.application.dto.result.ProductResult;
import com.product.domain.product.model.Product;
import com.product.domain.product.model.ProductAvailability;
import org.springframework.stereotype.Component;

@Component
public class ProductResultFactory {

    public ProductResult from(Product product) {
        if (product == null) {
            return null;
        }

        if (product.getStock() == null) {
            return new ProductResult(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    null,
                    null,
                    null,
                    null,
                    product.getUpdatedAt()
            );
        }

        ProductAvailability availability = ProductAvailability.from(product.getStock());

        return new ProductResult(
                product.getId(),
                product.getName(),
                product.getPrice(),
                null,
                product.getStock(),
                availability.isSoldOut(),
                availability.displayStatus().name(),
                product.getUpdatedAt()
        );
    }
}