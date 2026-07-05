package io.kelta.worker.interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ApiPaths#isMetadataPath(String)} — the shared reserved-path
 * classifier used by both the field-security advice and the masked-field predicate
 * guard.
 *
 * <p>The security-critical property is segment-boundary matching, not raw prefix
 * matching: a tenant user collection whose API name merely starts with a reserved
 * token ({@code flowsheet} vs {@code /api/flows}, {@code metrics2} vs
 * {@code /api/metrics}) must NOT be classified as metadata, or masking/FLS would be
 * silently skipped on real user data.
 */
@DisplayName("ApiPaths.isMetadataPath")
class ApiPathsTest {

    @Nested
    @DisplayName("Reserved metadata paths match")
    class ReservedMatch {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/collections",
                "/api/profiles",
                "/api/security-audit-logs",
                "/api/plugins",
                "/api/oidc",
                "/api/tenants",
                "/api/metrics",
                "/api/flows"
        })
        @DisplayName("Exact reserved path is metadata")
        void exactReservedPathMatches(String path) {
            assertThat(ApiPaths.isMetadataPath(path)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/collections/123",
                "/api/collections/contacts/fields",
                "/api/flows/abc/run",
                "/api/metrics/summary",
                "/api/profiles/p1",
                "/api/oidc/.well-known/openid-configuration"
        })
        @DisplayName("Sub-path under a reserved prefix is metadata")
        void subPathMatches(String path) {
            assertThat(ApiPaths.isMetadataPath(path)).isTrue();
        }
    }

    @Nested
    @DisplayName("Look-alike user collections do NOT match")
    class LookAlikeDoesNotMatch {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/flowsheet",            // vs /api/flows
                "/api/flowsheet/123",
                "/api/metrics2",             // vs /api/metrics
                "/api/collections_archive",  // vs /api/collections
                "/api/tenants_backup",       // vs /api/tenants
                "/api/oidc_audit",           // vs /api/oidc
                "/api/plugins_config",       // vs /api/plugins
                "/api/profiles_snapshot"     // vs /api/profiles
        })
        @DisplayName("A reserved token as a name prefix is still a user collection")
        void lookAlikePrefixIsNotMetadata(String path) {
            assertThat(ApiPaths.isMetadataPath(path)).isFalse();
        }
    }

    @Nested
    @DisplayName("Unrelated paths do not match")
    class UnrelatedDoesNotMatch {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/contacts",
                "/api/orders/1",
                "/api/accounts/acc-1/contacts",
                "/api/leads"
        })
        @DisplayName("Ordinary user-collection paths are not metadata")
        void unrelatedPathIsNotMetadata(String path) {
            assertThat(ApiPaths.isMetadataPath(path)).isFalse();
        }
    }
}
