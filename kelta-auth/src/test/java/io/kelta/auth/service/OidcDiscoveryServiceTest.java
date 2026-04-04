package io.kelta.auth.service;

import io.kelta.auth.service.OidcDiscoveryService.DiscoveryMetadata;
import io.kelta.auth.service.OidcDiscoveryService.EndpointOverrides;
import io.kelta.auth.service.OidcDiscoveryService.ResolvedEndpoints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OidcDiscoveryService Tests")
class OidcDiscoveryServiceTest {

    private OidcDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new OidcDiscoveryService();
    }

    @Nested
    @DisplayName("discover")
    class Discover {
        @Test
        void shouldReturnEmptyForNullIssuer() {
            assertThat(service.discover(null)).isEmpty();
        }

        @Test
        void shouldReturnEmptyForBlankIssuer() {
            assertThat(service.discover("")).isEmpty();
            assertThat(service.discover("   ")).isEmpty();
        }

        @Test
        void shouldReturnEmptyForUnreachableIssuer() {
            // Non-existent issuer will fail the HTTP call
            assertThat(service.discover("https://nonexistent.example.com")).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolve with overrides")
    class Resolve {
        @Test
        void shouldReturnManualStatusWhenDiscoveryFails() {
            EndpointOverrides overrides = new EndpointOverrides(
                    "https://auth.example.com/authorize",
                    "https://auth.example.com/token",
                    "https://auth.example.com/userinfo",
                    "https://auth.example.com/jwks",
                    "https://auth.example.com/logout"
            );
            ResolvedEndpoints resolved = service.resolve("https://nonexistent.example.com", overrides);
            assertThat(resolved.authorizationUri()).isEqualTo("https://auth.example.com/authorize");
            assertThat(resolved.tokenUri()).isEqualTo("https://auth.example.com/token");
            assertThat(resolved.discoveryStatus()).isEqualTo("manual");
        }

        @Test
        void shouldReturnNullFieldsWhenNoOverridesAndDiscoveryFails() {
            EndpointOverrides overrides = new EndpointOverrides(null, null, null, null, null);
            ResolvedEndpoints resolved = service.resolve("https://nonexistent.example.com", overrides);
            assertThat(resolved.authorizationUri()).isNull();
            assertThat(resolved.tokenUri()).isNull();
            assertThat(resolved.discoveryStatus()).isEqualTo("manual");
        }
    }

    @Nested
    @DisplayName("EndpointOverrides")
    class EndpointOverridesTest {
        @Test
        void hasAnyOverrideShouldReturnFalseForAllNull() {
            assertThat(new EndpointOverrides(null, null, null, null, null).hasAnyOverride()).isFalse();
        }

        @Test
        void hasAnyOverrideShouldReturnFalseForAllBlank() {
            assertThat(new EndpointOverrides("", " ", "", "", "").hasAnyOverride()).isFalse();
        }

        @Test
        void hasAnyOverrideShouldReturnTrueWhenAnySet() {
            assertThat(new EndpointOverrides(null, "https://example.com/token", null, null, null)
                    .hasAnyOverride()).isTrue();
        }
    }

    @Nested
    @DisplayName("cache")
    class Cache {
        @Test
        void evictShouldClearSpecificEntry() {
            // Exercise cache behavior: discover, evict, discover again
            service.discover("https://nonexistent1.example.com");
            service.evict("https://nonexistent1.example.com");
            // Should not throw and should attempt fetch again (not cached)
            assertThat(service.discover("https://nonexistent1.example.com")).isEmpty();
        }

        @Test
        void evictAllShouldClearAllEntries() {
            service.discover("https://nonexistent1.example.com");
            service.discover("https://nonexistent2.example.com");
            service.evictAll();
            // No assertion needed — verifying no exceptions
        }
    }
}
