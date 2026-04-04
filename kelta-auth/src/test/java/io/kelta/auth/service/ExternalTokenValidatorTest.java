package io.kelta.auth.service;

import io.kelta.auth.service.WorkerClient.OidcProviderInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ExternalTokenValidator")
@ExtendWith(MockitoExtension.class)
class ExternalTokenValidatorTest {

    @Mock private WorkerClient workerClient;

    private ExternalTokenValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ExternalTokenValidator(workerClient);
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("returns empty for non-JWT string")
        void returnsEmptyForNonJwt() {
            Optional<ExternalTokenValidator.ValidatedToken> result = validator.validate("not-a-jwt");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for empty string")
        void returnsEmptyForEmptyString() {
            Optional<ExternalTokenValidator.ValidatedToken> result = validator.validate("");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when issuer is missing from token")
        void returnsEmptyWhenNoIssuer() {
            // Craft a JWT with no issuer: header.payload.signature
            // Header: {"alg":"none"}, Payload: {"sub":"user1"} (no iss)
            String header = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"RS256\"}".getBytes());
            String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"user1\"}".getBytes());
            // Create a dummy signature (won't be verified since we fail before that)
            String signature = "dummysig";
            String jwt = header + "." + payload + "." + signature;

            Optional<ExternalTokenValidator.ValidatedToken> result = validator.validate(jwt);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when no OIDC provider found for issuer")
        void returnsEmptyWhenNoProvider() {
            String header = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"RS256\"}".getBytes());
            String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"iss\":\"https://unknown-idp.com\",\"sub\":\"user1\"}".getBytes());
            String signature = "dummysig";
            String jwt = header + "." + payload + "." + signature;

            when(workerClient.findOidcProviderByIssuer("https://unknown-idp.com", null))
                    .thenReturn(Optional.empty());

            Optional<ExternalTokenValidator.ValidatedToken> result = validator.validate(jwt);

            assertThat(result).isEmpty();
        }
    }
}
