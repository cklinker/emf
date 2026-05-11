package io.kelta.testharness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HarnessDbConfig#resolve")
class KeltaStackDbConfigTest {

    private static Function<String, String> env(Map<String, String> entries) {
        return entries::get;
    }

    @Nested
    @DisplayName("Local dev (CI_DB_JDBC_URL unset)")
    class LocalDev {

        @Test
        @DisplayName("falls back to Testcontainers PG defaults when env var is absent")
        void fallsBackWhenAbsent() {
            var cfg = HarnessDbConfig.resolve(env(Map.of()));

            assertThat(cfg.external()).isFalse();
            assertThat(cfg.jdbcUrl()).isEqualTo("jdbc:postgresql://postgres:5432/kelta_control_plane");
            assertThat(cfg.username()).isEqualTo("kelta");
            assertThat(cfg.password()).isEqualTo("kelta");
        }

        @Test
        @DisplayName("falls back when CI_DB_JDBC_URL is blank")
        void fallsBackWhenBlank() {
            var entries = new HashMap<String, String>();
            entries.put("CI_DB_JDBC_URL", "   ");
            var cfg = HarnessDbConfig.resolve(env(entries));

            assertThat(cfg.external()).isFalse();
        }
    }

    @Nested
    @DisplayName("CI pool (CI_DB_JDBC_URL set)")
    class CiPool {

        @Test
        @DisplayName("uses the external URL/user/password verbatim, preserving currentSchema")
        void usesExternalConfig() {
            var entries = Map.of(
                    "CI_DB_JDBC_URL", "jdbc:postgresql://kelta-ci-db-1:5432/ci?currentSchema=ci_42_1",
                    "CI_DB_USER", "ci",
                    "CI_DB_PASSWORD", "secret");

            var cfg = HarnessDbConfig.resolve(env(entries));

            assertThat(cfg.external()).isTrue();
            assertThat(cfg.jdbcUrl())
                    .isEqualTo("jdbc:postgresql://kelta-ci-db-1:5432/ci?currentSchema=ci_42_1")
                    .contains("currentSchema=ci_42_1");
            assertThat(cfg.username()).isEqualTo("ci");
            assertThat(cfg.password()).isEqualTo("secret");
        }

        @Test
        @DisplayName("rejects missing CI_DB_USER with a clear error")
        void rejectsMissingUser() {
            var entries = Map.of(
                    "CI_DB_JDBC_URL", "jdbc:postgresql://h/ci?currentSchema=s",
                    "CI_DB_PASSWORD", "pw");

            assertThatThrownBy(() -> HarnessDbConfig.resolve(env(entries)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CI_DB_USER");
        }

        @Test
        @DisplayName("rejects missing CI_DB_PASSWORD with a clear error")
        void rejectsMissingPassword() {
            var entries = Map.of(
                    "CI_DB_JDBC_URL", "jdbc:postgresql://h/ci?currentSchema=s",
                    "CI_DB_USER", "ci");

            assertThatThrownBy(() -> HarnessDbConfig.resolve(env(entries)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CI_DB_PASSWORD");
        }
    }
}
