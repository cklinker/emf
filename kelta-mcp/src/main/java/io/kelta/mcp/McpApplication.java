package io.kelta.mcp;

import io.kelta.crypto.EncryptionAutoConfiguration;
import io.kelta.runtime.config.KeltaRuntimeAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * kelta-mcp is a stateless protocol adapter — it has no database, no Flyway,
 * and no JPA. We use {@code runtime-core} for type definitions only
 * ({@code CollectionDefinition}, {@code FieldDefinition}, {@code FieldType}),
 * not for its Spring beans. Component scan is restricted to {@code io.kelta.mcp},
 * and runtime-core's auto-configurations are excluded so we don't drag in a
 * JdbcTemplate-backed schema migration engine, an encryption setup that needs
 * platform secrets, or a Tomcat customizer meant for the data-plane.
 */
@SpringBootApplication(
        scanBasePackages = "io.kelta.mcp",
        exclude = {KeltaRuntimeAutoConfiguration.class, EncryptionAutoConfiguration.class}
)
@ConfigurationPropertiesScan
public class McpApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpApplication.class, args);
    }
}
