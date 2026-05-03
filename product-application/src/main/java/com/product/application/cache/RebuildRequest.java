package com.product.application.cache;

import java.util.LinkedHashSet;
import java.util.List;

public record RebuildRequest(
        boolean allProducts,
        List<Long> targetProductIds,
        long totalCount,
        int chunkSize,
        String filterSummary
) {
    private static final int DEFAULT_CHUNK_SIZE = 500;

    public RebuildRequest {
        targetProductIds = normalizeIds(targetProductIds);
        totalCount = allProducts ? Math.max(totalCount, 0L) : targetProductIds.size();
        chunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        filterSummary = (filterSummary == null || filterSummary.isBlank()) ? "ALL" : filterSummary;
    }

    public RebuildRequest(List<Long> targetProductIds, int chunkSize, String filterSummary) {
        this(false, targetProductIds, 0L, chunkSize, filterSummary);
    }

    public static RebuildRequest allProducts(long totalCount, int chunkSize) {
        return new RebuildRequest(true, List.of(), totalCount, chunkSize, "ALL");
    }

    public boolean isEmpty() {
        return totalCount <= 0;
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
