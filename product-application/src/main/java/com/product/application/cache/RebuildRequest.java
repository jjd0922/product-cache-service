package com.product.application.cache;

import java.util.LinkedHashSet;
import java.util.List;

public record RebuildRequest(
        List<Long> targetProductIds,
        int chunkSize,
        String filterSummary
) {
    private static final int DEFAULT_CHUNK_SIZE = 500;

    public RebuildRequest {
        targetProductIds = normalizeIds(targetProductIds);
        chunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        filterSummary = (filterSummary == null || filterSummary.isBlank()) ? "ALL" : filterSummary;
    }

    public long totalCount() {
        return targetProductIds.size();
    }

    public boolean isEmpty() {
        return targetProductIds.isEmpty();
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        return List.copyOf(normalized);
    }
}