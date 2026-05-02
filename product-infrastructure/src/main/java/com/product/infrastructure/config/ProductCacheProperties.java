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
    private long detailTtlJitterSeconds = 0;

    /** 런타임 캐시(Hash) */
    private String runtimeKeyPrefix = "product:runtime:";
    private long runtimeTtlSeconds = 300;
    private long runtimeTtlJitterSeconds = 0;

    /** pipeline batch size */
    private int pipelineBatchSize = 500;

    /** rebuild job store: in-memory or redis */
    private String rebuildJobStore = "in-memory";
    private String rebuildJobKeyPrefix = "product:cache:rebuild:job:";
    private String rebuildActiveKey = "product:cache:rebuild:active";
    private long rebuildJobTtlSeconds = 86400;
    private long rebuildActiveTtlSeconds = 21600;
}
