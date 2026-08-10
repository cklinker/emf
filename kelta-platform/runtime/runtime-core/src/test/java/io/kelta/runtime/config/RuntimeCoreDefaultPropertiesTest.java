package io.kelta.runtime.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the defaults that runtime-core ships inside its jar.
 *
 * <p>{@code application.properties} packaged in a library is loaded by Spring Boot as
 * application configuration for every service that depends on it — kelta-worker, kelta-ai,
 * kelta-mcp and kelta-auth. Anything added here silently becomes those services' behaviour,
 * and looks like their bug rather than this module's.
 *
 * <p>This is not hypothetical: a stray {@code logging.level.org.springframework.jdbc=DEBUG}
 * lived here long enough to put every SQL statement into production logs — 493k lines a day
 * on kelta-worker alone, roughly 92% of the platform's total log volume.
 */
@DisplayName("runtime-core packaged defaults")
class RuntimeCoreDefaultPropertiesTest {

    private Properties load() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                return props; // file removed entirely — also fine
            }
            props.load(in);
        }
        return props;
    }

    @Test
    @DisplayName("does not set log levels for consuming services")
    void shipsNoLoggingLevels() throws Exception {
        Properties props = load();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("logging.")) {
                fail("runtime-core's application.properties must not set '" + key + "'. It is "
                        + "packaged in the jar, so it silently overrides log levels for every "
                        + "service that depends on runtime-core. Each service owns its own "
                        + "levels via logback-spring.xml / LOGGING_LEVEL_* env vars.");
            }
        }
    }

    @Test
    @DisplayName("does not enable debug logging for any package")
    void shipsNoDebugLevels() throws Exception {
        Properties props = load();
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            assertTrue(value == null || !value.equalsIgnoreCase("DEBUG") && !value.equalsIgnoreCase("TRACE"),
                    "runtime-core's application.properties sets " + key + "=" + value
                            + ", which turns on verbose logging for every consuming service");
        }
    }
}
