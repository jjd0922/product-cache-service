package com.product.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
public class ProductCacheRebuildRequest {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedSince;
    private Long productIdFrom;
    private Long productIdTo;
    private Integer chunkSize;
}
