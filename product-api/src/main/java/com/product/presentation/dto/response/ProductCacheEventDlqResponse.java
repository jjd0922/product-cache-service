package com.product.presentation.dto.response;

import java.time.Instant;

public record ProductCacheEventDlqResponse(
        String eventId,
        Long productId,
        String changeType,
        String failureReason,
        Instant createdAt
) {
}
