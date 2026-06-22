package io.kelta.worker.service.storage;

import io.kelta.runtime.storage.ExternalJdbcConnectionProvider.JdbcConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PooledExternalJdbcConnectionProvider")
class PooledExternalJdbcConnectionProviderTest {

    private final PooledExternalJdbcConnectionProvider provider = new PooledExternalJdbcConnectionProvider();

    @Test
    @DisplayName("reuses one pooled template per jdbcUrl+user")
    void cachesPerConnection() {
        JdbcTemplate first = provider.jdbcTemplate(new JdbcConfig("jdbc:demo:pool1", "sa", ""));
        JdbcTemplate second = provider.jdbcTemplate(new JdbcConfig("jdbc:demo:pool1", "sa", ""));
        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("creates distinct pools for different connections")
    void distinctPerConnection() {
        JdbcTemplate a = provider.jdbcTemplate(new JdbcConfig("jdbc:demo:poolA", "sa", ""));
        JdbcTemplate b = provider.jdbcTemplate(new JdbcConfig("jdbc:demo:poolB", "sa", ""));
        JdbcTemplate c = provider.jdbcTemplate(new JdbcConfig("jdbc:demo:poolA", "other", ""));
        assertThat(a).isNotSameAs(b);
        assertThat(a).isNotSameAs(c); // same url, different user → different pool
    }
}
