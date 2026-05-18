package com.product.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "product.cache")
public class ProductCacheProperties {

    private String keyPrefix = "product";
    private String keyVersion = "v1";

    private long detailTtlSeconds = 1800;
    private long detailTtlJitterSeconds = 0;

    private long runtimeTtlSeconds = 300;
    private long runtimeTtlJitterSeconds = 0;

    private long notFoundTtlSeconds = 60;

    /** pipeline batch size */
    private int pipelineBatchSize = 500;

    /** rebuild job store: in-memory or redis */
    private String rebuildJobStore = "in-memory";
    private String rebuildJobKeyPrefix = "product:cache:rebuild:job:";
    private String rebuildActiveKey = "product:cache:rebuild:active";
    private long rebuildJobTtlSeconds = 86400;
    private long rebuildActiveTtlSeconds = 21600;

    private String singleFlightLockKeyPrefix = "product:singleflight:";
    private long singleFlightLockLeaseMillis = 3000;

    private float circuitFailureRateThreshold = 50.0f;
    private int circuitSlidingWindowSize = 20;
    private int circuitMinimumNumberOfCalls = 5;
    private long circuitWaitDurationInOpenStateMillis = 30000;
    private int circuitPermittedCallsInHalfOpenState = 3;

    private String eventDlqStreamKey = "product:cache:event:dlq";
    private int eventDlqMaxReadCount = 100;
}
