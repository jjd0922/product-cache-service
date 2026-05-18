package infrastructure.cache.support;

import com.product.infrastructure.cache.support.ProductCacheKeyGenerator;
import com.product.infrastructure.config.ProductCacheProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCacheKeyGeneratorTest {

    @Test
    void keyMethods_useConfiguredVersionPrefix() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setKeyPrefix("product");
        properties.setKeyVersion("v2");
        ProductCacheKeyGenerator keyGenerator = new ProductCacheKeyGenerator(properties);

        assertThat(keyGenerator.detailKey(1L)).isEqualTo("product:v2:detail:1");
        assertThat(keyGenerator.runtimeKey(1L)).isEqualTo("product:v2:runtime:1");
        assertThat(keyGenerator.notFoundKey(1L)).isEqualTo("product:v2:notfound:1");
    }

    @Test
    void keyMethods_whenVersionChanges_thenIgnoreOldPrefixAndUseNewPrefix() {
        ProductCacheProperties properties = new ProductCacheProperties();
        ProductCacheKeyGenerator keyGenerator = new ProductCacheKeyGenerator(properties);

        properties.setKeyVersion("v1");
        String oldVersionKey = keyGenerator.detailKey(1L);

        properties.setKeyVersion("v2");
        String newVersionKey = keyGenerator.detailKey(1L);

        assertThat(oldVersionKey).isEqualTo("product:v1:detail:1");
        assertThat(newVersionKey).isEqualTo("product:v2:detail:1");
        assertThat(newVersionKey).isNotEqualTo(oldVersionKey);
    }
}
