package com.product.presentation.dto.request;

import java.util.List;

public record RebuildRequest(
        List<Long> productIds
) {
}