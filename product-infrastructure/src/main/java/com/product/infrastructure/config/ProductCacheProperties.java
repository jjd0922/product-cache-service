package com.product.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "product.cache")
public class ProductCacheProperties {

    /** 상세 캐시(JSON String) */
    private String detailKeyPrefix = "product:detail:";
    private long detailTtlSeconds = 1800;

    /** pipeline batch size */
    private int pipelineBatchSize = 500;
}
