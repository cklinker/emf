package io.kelta.worker.config;

import io.kelta.runtime.context.TenantContext;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the {@link TenantContext} platform sentinel around Flyway's migration run
 * so the wrapped {@code TenantAwareDataSource} can fail closed on a blank tenant
 * context without breaking the migration step at boot.
 *
 * <p>Flyway executes DDL that is not subject to Row Level Security, but every
 * connection it checks out from the pool still runs
 * {@code SET app.current_tenant_id = '…'} — that value must be {@code __platform__}
 * so the {@code platform_bypass} policy matches when any migration issues DML.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayPlatformContextStrategy() {
        return flyway -> TenantContext.runAsPlatform(flyway::migrate);
    }
}
