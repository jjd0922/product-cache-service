package infrastructure.cache.support;

import com.product.infrastructure.cache.support.ProductCacheTtlPolicy;
import com.product.infrastructure.config.ProductCacheProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCacheTtlPolicyTest {

    @Test
    void detailTtlSeconds_returnsBaseTtlWhenJitterIsDisabled() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setDetailTtlSeconds(300L);
        properties.setDetailTtlJitterSeconds(0L);

        ProductCacheTtlPolicy policy = new ProductCacheTtlPolicy(properties);

        assertThat(policy.detailTtlSeconds()).isEqualTo(300L);
    }

    @Test
    void runtimeTtlSeconds_appliesJitterWithinConfiguredRange() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setRuntimeTtlSeconds(300L);
        properties.setRuntimeTtlJitterSeconds(30L);

        ProductCacheTtlPolicy policy = new ProductCacheTtlPolicy(properties);

        assertThat(policy.runtimeTtlSeconds()).isBetween(300L, 330L);
    }

    @Test
    void ttlSeconds_usesMinimumOneSecondWhenBaseTtlIsInvalid() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setDetailTtlSeconds(0L);

        ProductCacheTtlPolicy policy = new ProductCacheTtlPolicy(properties);

        assertThat(policy.detailTtlSeconds()).isEqualTo(1L);
    }
}
