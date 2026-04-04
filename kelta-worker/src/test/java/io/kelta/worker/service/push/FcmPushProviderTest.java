package io.kelta.worker.service.push;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmPushProvider Tests")
class FcmPushProviderTest {

    private static PrivateKey testPrivateKey;
    private static String testPrivateKeyPem;

    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec bodyUriSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private FcmPushProvider provider;

    @BeforeAll
    static void generateTestKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        testPrivateKey = kp.getPrivate();
        testPrivateKeyPem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(testPrivateKey.getEncoded()) +
                "\n-----END PRIVATE KEY-----";
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        provider = new FcmPushProvider("test-project", "sa@test.iam.gserviceaccount.com",
                testPrivateKey, restClient, objectMapper);
        // Pre-populate token cache so tests don't need to mock token exchange
        provider.putCachedToken("sa@test.iam.gserviceaccount.com", "ya29.test-token");
    }

    @SuppressWarnings("unchecked")
    private void setupFcmSend(String sendResponse) {
        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Map.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(sendResponse);
    }

    @SuppressWarnings("unchecked")
    private void setupFcmSendWithError(int statusCode, String errorBody) {
        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Map.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.body(String.class)).thenReturn(errorBody);

        when(responseSpec.onStatus(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var predicate = (java.util.function.Predicate<HttpStatusCode>) invocation.getArgument(0);
            HttpStatusCode code = HttpStatusCode.valueOf(statusCode);
            if (predicate.test(code)) {
                var handler = (RestClient.ResponseSpec.ErrorHandler) invocation.getArgument(1);
                var mockResponse = mock(ClientHttpResponse.class, withSettings().lenient());
                when(mockResponse.getBody()).thenReturn(
                        new ByteArrayInputStream(errorBody.getBytes(StandardCharsets.UTF_8)));
                when(mockResponse.getStatusCode()).thenReturn(code);
                handler.handle(null, mockResponse);
            }
            return responseSpec;
        });
    }

    @Nested
    @DisplayName("FCM Send")
    class FcmSend {

        @Test
        void shouldSendSuccessfully() {
            setupFcmSend("{\"name\":\"projects/test-project/messages/12345\"}");
            var message = new PushMessage("device-token-1", "android", "Title", "Body", null);
            assertThatCode(() -> provider.send(message, null)).doesNotThrowAnyException();
        }

        @Test
        void shouldSendToCorrectProjectUrl() {
            setupFcmSend("{\"name\":\"projects/test-project/messages/1\"}");
            var message = new PushMessage("tok", "ios", "Title", "Body", null);
            provider.send(message, null);
            verify(bodyUriSpec).uri(contains("test-project"));
        }
    }

    @Nested
    @DisplayName("FCM Payload")
    class FcmPayload {

        @Test
        @SuppressWarnings("unchecked")
        void shouldIncludeAndroidHighPriority() {
            setupFcmSend("{\"name\":\"projects/test-project/messages/1\"}");
            var message = new PushMessage("tok", "android", "Title", "Body", Map.of("key", "val"));
            provider.send(message, null);

            verify(bodySpec).body(argThat((Map<String, Object> body) -> {
                var msg = (Map<String, Object>) body.get("message");
                return msg.containsKey("android") && msg.containsKey("data")
                        && msg.containsKey("notification");
            }));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldIncludeApnsPriority() {
            setupFcmSend("{\"name\":\"projects/test-project/messages/1\"}");
            var message = new PushMessage("tok", "ios", "Title", "Body", null);
            provider.send(message, null);

            verify(bodySpec).body(argThat((Map<String, Object> body) -> {
                var msg = (Map<String, Object>) body.get("message");
                return msg.containsKey("apns");
            }));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldIncludeWebpushHeaders() {
            setupFcmSend("{\"name\":\"projects/test-project/messages/1\"}");
            var message = new PushMessage("tok", "web", "Title", "Body", null);
            provider.send(message, null);

            verify(bodySpec).body(argThat((Map<String, Object> body) -> {
                var msg = (Map<String, Object>) body.get("message");
                return msg.containsKey("webpush");
            }));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldOmitDataWhenNull() {
            setupFcmSend("{\"name\":\"projects/test-project/messages/1\"}");
            var message = new PushMessage("tok", "android", "Title", "Body", null);
            provider.send(message, null);

            verify(bodySpec).body(argThat((Map<String, Object> body) -> {
                var msg = (Map<String, Object>) body.get("message");
                return !msg.containsKey("data");
            }));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void shouldThrowInvalidTokenOnNotFound() {
            setupFcmSendWithError(404,
                    "{\"error\":{\"status\":\"NOT_FOUND\",\"message\":\"Requested entity was not found.\"}}");

            var message = new PushMessage("bad-token", "ios", "Title", "Body", null);
            assertThatThrownBy(() -> provider.send(message, null))
                    .isInstanceOf(PushDeliveryException.class)
                    .satisfies(e -> assertThat(((PushDeliveryException) e).isInvalidToken()).isTrue());
        }

        @Test
        void shouldThrowInvalidTokenOnInvalidArgument() {
            setupFcmSendWithError(400,
                    "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"not a valid FCM registration token\"}}");

            var message = new PushMessage("bad-token", "android", "Title", "Body", null);
            assertThatThrownBy(() -> provider.send(message, null))
                    .isInstanceOf(PushDeliveryException.class)
                    .satisfies(e -> assertThat(((PushDeliveryException) e).isInvalidToken()).isTrue());
        }

        @Test
        void shouldThrowNonInvalidTokenOnServerError() {
            setupFcmSendWithError(500, "Internal Server Error");

            var message = new PushMessage("tok", "android", "Title", "Body", null);
            assertThatThrownBy(() -> provider.send(message, null))
                    .isInstanceOf(PushDeliveryException.class)
                    .satisfies(e -> assertThat(((PushDeliveryException) e).isInvalidToken()).isFalse());
        }

        @Test
        void shouldThrowWhenNoCredentials() {
            var noCredProvider = new FcmPushProvider(null, null, null, restClient, objectMapper);
            var message = new PushMessage("tok", "ios", "Title", "Body", null);

            assertThatThrownBy(() -> noCredProvider.send(message, null))
                    .isInstanceOf(PushDeliveryException.class)
                    .hasMessageContaining("No FCM project ID configured");
        }

        @Test
        void shouldThrowWhenNoCredentialsAndNoTenant() {
            var noCredProvider = new FcmPushProvider("project", null, null, restClient, objectMapper);
            var message = new PushMessage("tok", "ios", "Title", "Body", null);

            assertThatThrownBy(() -> noCredProvider.send(message, null))
                    .isInstanceOf(PushDeliveryException.class)
                    .hasMessageContaining("No FCM credentials configured");
        }
    }

    @Nested
    @DisplayName("Tenant Settings Override")
    class TenantSettingsOverride {

        @Test
        void shouldUseTenantProject() {
            var tenantSettings = new TenantPushSettings("tenant-project", "sa@tenant.iam", testPrivateKeyPem);
            provider.putCachedToken("sa@tenant.iam", "ya29.tenant-token");

            setupFcmSend("{\"name\":\"projects/tenant-project/messages/1\"}");

            var message = new PushMessage("tok", "android", "Title", "Body", null);
            provider.send(message, tenantSettings);

            verify(bodyUriSpec).uri(contains("tenant-project"));
        }

        @Test
        void shouldFallBackToPlatformCredentials() {
            setupFcmSend("{\"name\":\"projects/test-project/messages/1\"}");

            var message = new PushMessage("tok", "ios", "Title", "Body", null);
            provider.send(message, null);

            verify(bodyUriSpec).uri(contains("test-project"));
        }

        @Test
        void shouldFallBackWhenTenantSettingsIncomplete() {
            var partialSettings = new TenantPushSettings("proj", null, null);

            setupFcmSend("{\"name\":\"projects/test-project/messages/1\"}");

            var message = new PushMessage("tok", "web", "Title", "Body", null);
            provider.send(message, partialSettings);

            verify(bodyUriSpec).uri(contains("test-project"));
        }
    }

    @Nested
    @DisplayName("Private Key Parsing")
    class PrivateKeyParsing {

        @Test
        void shouldParseValidPemKey() {
            PrivateKey key = FcmPushProvider.parsePrivateKey(testPrivateKeyPem);
            assertThat(key).isNotNull();
            assertThat(key.getAlgorithm()).isEqualTo("RSA");
        }

        @Test
        void shouldRejectInvalidKey() {
            assertThatThrownBy(() -> FcmPushProvider.parsePrivateKey("not-a-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid RSA private key");
        }
    }

    @Nested
    @DisplayName("TenantPushSettings")
    class TenantPushSettingsTests {

        @Test
        void shouldParseFromJsonNode() throws Exception {
            String json = "{\"push\":{\"fcm\":{\"projectId\":\"proj\",\"clientEmail\":\"sa@test.iam\",\"privateKey\":\"pk\"}}}";
            var node = objectMapper.readTree(json);
            var settings = TenantPushSettings.fromJsonNode(node);

            assertThat(settings).isNotNull();
            assertThat(settings.fcmProjectId()).isEqualTo("proj");
            assertThat(settings.fcmClientEmail()).isEqualTo("sa@test.iam");
            assertThat(settings.hasFcmOverride()).isTrue();
        }

        @Test
        void shouldReturnNullForMissingSection() throws Exception {
            var node = objectMapper.readTree("{}");
            assertThat(TenantPushSettings.fromJsonNode(node)).isNull();
        }

        @Test
        void shouldReturnNullForNullJson() {
            assertThat(TenantPushSettings.fromJsonNode(null)).isNull();
        }

        @Test
        void shouldReturnNullForMissingFcmSection() throws Exception {
            var node = objectMapper.readTree("{\"push\":{}}");
            assertThat(TenantPushSettings.fromJsonNode(node)).isNull();
        }

        @Test
        void shouldDetectIncompleteOverride() {
            var settings = new TenantPushSettings("proj", null, null);
            assertThat(settings.hasFcmOverride()).isFalse();
        }

        @Test
        void shouldMaskPrivateKeyInToString() {
            var settings = new TenantPushSettings("proj", "sa@test.iam", "secret-key");
            assertThat(settings.toString()).doesNotContain("secret-key");
            assertThat(settings.toString()).contains("****");
        }
    }

    @Nested
    @DisplayName("Token Exchange")
    class TokenExchange {

        @Test
        void shouldExchangeJwtForAccessToken() {
            // Create provider without pre-cached token
            var freshProvider = new FcmPushProvider("test-project", "sa@fresh.iam",
                    testPrivateKey, restClient, objectMapper);

            // Mock token exchange
            when(restClient.post()).thenReturn(bodyUriSpec);
            when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
            when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
            when(bodySpec.body(anyString())).thenReturn(bodySpec);
            when(bodySpec.headers(any())).thenReturn(bodySpec);
            lenient().when(bodySpec.body(any(Map.class))).thenReturn(bodySpec);
            when(bodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            // First call returns token, second call returns FCM response
            when(responseSpec.body(String.class))
                    .thenReturn("{\"access_token\":\"ya29.exchanged\",\"expires_in\":3600}")
                    .thenReturn("{\"name\":\"projects/test-project/messages/1\"}");

            var message = new PushMessage("tok", "android", "Title", "Body", null);
            assertThatCode(() -> freshProvider.send(message, null)).doesNotThrowAnyException();
        }
    }
}
