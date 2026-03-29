package com.product.application.dto.command;

import java.util.List;

public record ProductCacheRebuildCommand(
        List<Long> productIds
) {
}