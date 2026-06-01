package io.kelta.gateway.filter;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomDomainFilter Tests")
class CustomDomainFilterTest {

    @Nested
    @DisplayName("Host Sanitization")
    class HostSanitization {
        @Test
        void shouldStripPort() {
            assertThat(CustomDomainFilter.sanitizeHost("app.acme.com:443")).isEqualTo("app.acme.com");
        }

        @Test
        void shouldLowercase() {
            assertThat(CustomDomainFilter.sanitizeHost("App.ACME.com")).isEqualTo("app.acme.com");
        }

        @Test
        void shouldStripTrailingDot() {
            assertThat(CustomDomainFilter.sanitizeHost("app.acme.com.")).isEqualTo("app.acme.com");
        }

        @Test
        void shouldRejectNull() {
            assertThat(CustomDomainFilter.sanitizeHost(null)).isNull();
        }

        @Test
        void shouldRejectEmpty() {
            assertThat(CustomDomainFilter.sanitizeHost("")).isNull();
        }

        @Test
        void shouldRejectDoubleDots() {
            assertThat(CustomDomainFilter.sanitizeHost("app..acme.com")).isNull();
        }

        @Test
        void shouldAcceptValidDomain() {
            assertThat(CustomDomainFilter.sanitizeHost("app.acme.com")).isEqualTo("app.acme.com");
        }

        @Test
        void shouldAcceptSubdomain() {
            assertThat(CustomDomainFilter.sanitizeHost("my-app.acme.co.uk")).isEqualTo("my-app.acme.co.uk");
        }
    }

    @Nested
    @DisplayName("Reserved Hosts")
    class ReservedHosts {
        @Test
        void shouldReserveKeltaApex() {
            assertThat(CustomDomainFilter.isReservedHost("kelta.io")).isTrue();
        }

        @Test
        void shouldReserveKeltaSubdomains() {
            assertThat(CustomDomainFilter.isReservedHost("app.kelta.io")).isTrue();
            assertThat(CustomDomainFilter.isReservedHost("auth.kelta.io")).isTrue();
            assertThat(CustomDomainFilter.isReservedHost("api.kelta.io")).isTrue();
            assertThat(CustomDomainFilter.isReservedHost("mcp.kelta.io")).isTrue();
        }

        @Test
        void shouldReserveLocalhost() {
            assertThat(CustomDomainFilter.isReservedHost("localhost")).isTrue();
        }

        @Test
        void shouldNotReserveCustomerDomain() {
            assertThat(CustomDomainFilter.isReservedHost("acme.com")).isFalse();
            assertThat(CustomDomainFilter.isReservedHost("app.acme.com")).isFalse();
        }
    }
}
