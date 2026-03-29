package com.product.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;

public record RebuildJobResult(
        UUID jobId,
        String status,
        long total,
        long processed,
        int progressPercent,
        boolean active,
        String message,
        String failureReason,
        String filterSummary,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}