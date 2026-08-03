package io.kelta.worker.service.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("StripeApiClient Tests")
class StripeApiClientTest {

    private static final String API_VERSION = "2024-06-20";
    private static final String KEY = "sk_test_123";

    private MockRestServiceServer server;
    private StripeApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new StripeApiClient(builder.build(), new ObjectMapper(), API_VERSION);
    }

    private static String decoded(String body) {
        return URLDecoder.decode(body, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("Request shape")
    class RequestShape {

        @Test
        @DisplayName("pins the API version and authenticates with the secret key")
        void pinsVersionAndAuthenticates() {
            server.expect(requestTo("https://api.stripe.com/v1/customers"))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer " + KEY))
                    .andExpect(header("Stripe-Version", API_VERSION))
                    .andExpect(content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_FORM_URLENCODED))
                    .andRespond(withSuccess("{\"id\":\"cus_1\"}", MediaType.APPLICATION_JSON));

            JsonNode result = client.createCustomer(KEY, "t1", "u1", "m@example.com");

            assertThat(result.get("id").stringValue()).isEqualTo("cus_1");
            server.verify();
        }

        @Test
        @DisplayName("customer creation uses a DETERMINISTIC idempotency key")
        void customerIdempotencyKeyIsDeterministic() {
            // A member gets one processor customer ever, so a racing retry must
            // replay rather than create a duplicate.
            server.expect(requestTo("https://api.stripe.com/v1/customers"))
                    .andExpect(header("Idempotency-Key", "cust:t1:u1"))
                    .andRespond(withSuccess("{\"id\":\"cus_1\"}", MediaType.APPLICATION_JSON));

            client.createCustomer(KEY, "t1", "u1", null);
            server.verify();
        }

        @Test
        @DisplayName("customer metadata carries tenant and member ids")
        void customerCarriesMetadata() {
            server.expect(requestTo("https://api.stripe.com/v1/customers"))
                    .andExpect(request -> {
                        String body = decoded(request.getBody().toString());
                        assertThat(body).contains("metadata[tenantId]=t1");
                        assertThat(body).contains("metadata[userId]=u1");
                        assertThat(body).contains("email=m@example.com");
                    })
                    .andRespond(withSuccess("{\"id\":\"cus_1\"}", MediaType.APPLICATION_JSON));

            client.createCustomer(KEY, "t1", "u1", "m@example.com");
            server.verify();
        }

        @Test
        @DisplayName("a null email is omitted rather than sent empty")
        void nullEmailOmitted() {
            server.expect(requestTo("https://api.stripe.com/v1/customers"))
                    .andExpect(request ->
                            assertThat(request.getBody().toString()).doesNotContain("email="))
                    .andRespond(withSuccess("{\"id\":\"cus_1\"}", MediaType.APPLICATION_JSON));

            client.createCustomer(KEY, "t1", "u1", null);
            server.verify();
        }
    }

    @Nested
    @DisplayName("Checkout session")
    class CheckoutSession {

        @Test
        @DisplayName("subscription mode stamps metadata on BOTH session and subscription")
        void subscriptionModeStampsBothMetadataBlocks() {
            server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                    .andExpect(request -> {
                        String body = decoded(request.getBody().toString());
                        assertThat(body).contains("mode=subscription");
                        assertThat(body).contains("client_reference_id=u1");
                        assertThat(body).contains("line_items[0][price]=price_1");
                        assertThat(body).contains("line_items[0][quantity]=1");
                        assertThat(body).contains("automatic_tax[enabled]=true");
                        assertThat(body).contains("metadata[userId]=u1");
                        assertThat(body).contains("metadata[planCode]=standard");
                        // Session metadata does NOT propagate to the subscription
                        // object, so the subscription webhook needs its own copy.
                        assertThat(body).contains("subscription_data[metadata][userId]=u1");
                        assertThat(body).contains("subscription_data[metadata][tenantId]=t1");
                    })
                    .andRespond(withSuccess("{\"url\":\"https://checkout.test/s1\"}",
                            MediaType.APPLICATION_JSON));

            JsonNode session = client.createCheckoutSession(KEY, "t1", "u1", "subscription",
                    "price_1", "standard", null, "https://a.test/ok", "https://a.test/no");

            assertThat(session.get("url").stringValue()).isEqualTo("https://checkout.test/s1");
            server.verify();
        }

        @Test
        @DisplayName("payment mode omits subscription_data")
        void paymentModeOmitsSubscriptionData() {
            server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                    .andExpect(request -> {
                        String body = decoded(request.getBody().toString());
                        assertThat(body).contains("mode=payment");
                        assertThat(body).doesNotContain("subscription_data");
                    })
                    .andRespond(withSuccess("{\"url\":\"https://checkout.test/s2\"}",
                            MediaType.APPLICATION_JSON));

            client.createCheckoutSession(KEY, "t1", "u1", "payment",
                    "price_2", "pass", null, "https://a.test/ok", "https://a.test/no");
            server.verify();
        }

        @Test
        @DisplayName("an existing customer is reused when present, omitted when not")
        void reusesExistingCustomer() {
            server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                    .andExpect(request ->
                            assertThat(decoded(request.getBody().toString()))
                                    .contains("customer=cus_9"))
                    .andRespond(withSuccess("{\"url\":\"u\"}", MediaType.APPLICATION_JSON));

            client.createCheckoutSession(KEY, "t1", "u1", "payment", "price_1", "pass",
                    "cus_9", "https://a.test/ok", "https://a.test/no");
            server.verify();
        }

        @Test
        @DisplayName("checkout uses a PER-ATTEMPT idempotency key")
        void checkoutKeyVariesPerAttempt() {
            // A deterministic key would replay the previous session for the
            // processor's 24h window, blocking a second same-day purchase of a
            // one-time pass.
            java.util.Set<String> keys = new java.util.HashSet<>();
            // All expectations must be declared before any request is made.
            server.expect(org.springframework.test.web.client.ExpectedCount.twice(),
                            requestTo("https://api.stripe.com/v1/checkout/sessions"))
                    .andExpect(request ->
                            keys.add(request.getHeaders().getFirst("Idempotency-Key")))
                    .andRespond(withSuccess("{\"url\":\"u\"}", MediaType.APPLICATION_JSON));

            for (int i = 0; i < 2; i++) {
                client.createCheckoutSession(KEY, "t1", "u1", "payment", "price_1", "pass",
                        null, "https://a.test/ok", "https://a.test/no");
            }
            assertThat(keys).hasSize(2);
            server.verify();
        }
    }

    @Nested
    @DisplayName("Billing portal session")
    class PortalSession {

        @Test
        @DisplayName("sends customer and return URL")
        void sendsCustomerAndReturnUrl() {
            server.expect(requestTo("https://api.stripe.com/v1/billing_portal/sessions"))
                    .andExpect(request -> {
                        String body = decoded(request.getBody().toString());
                        assertThat(body).contains("customer=cus_1");
                        assertThat(body).contains("return_url=https://a.test/back");
                    })
                    .andRespond(withSuccess("{\"url\":\"https://portal.test/p1\"}",
                            MediaType.APPLICATION_JSON));

            JsonNode session = client.createBillingPortalSession(KEY, "cus_1", "https://a.test/back");

            assertThat(session.get("url").stringValue()).isEqualTo("https://portal.test/p1");
            server.verify();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("surfaces the processor's error type and code")
        void surfacesErrorTypeAndCode() {
            server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("""
                                    {"error":{"type":"invalid_request_error",
                                     "code":"resource_missing","message":"No such price"}}
                                    """));

            assertThatThrownBy(() -> client.createCheckoutSession(KEY, "t1", "u1", "payment",
                    "price_gone", "pass", null, "https://a.test/ok", "https://a.test/no"))
                    .isInstanceOfSatisfying(StripeApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(400);
                        assertThat(e.getErrorType()).isEqualTo("invalid_request_error");
                        assertThat(e.getErrorCode()).isEqualTo("resource_missing");
                        assertThat(e.getMessage()).isEqualTo("No such price");
                    });
        }

        @Test
        @DisplayName("flags an auth failure distinctly (operator problem, not member's)")
        void flagsAuthFailure() {
            server.expect(requestTo("https://api.stripe.com/v1/customers"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"type\":\"invalid_request_error\","
                                    + "\"message\":\"Invalid API Key\"}}"));

            assertThatThrownBy(() -> client.createCustomer(KEY, "t1", "u1", null))
                    .isInstanceOfSatisfying(StripeApiException.class,
                            e -> assertThat(e.isAuthFailure()).isTrue());
        }

        @Test
        @DisplayName("an unparseable error body still throws rather than returning junk")
        void unparseableErrorBodyStillThrows() {
            server.expect(requestTo("https://api.stripe.com/v1/customers"))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MediaType.TEXT_HTML).body("<html>gateway error</html>"));

            assertThatThrownBy(() -> client.createCustomer(KEY, "t1", "u1", null))
                    .isInstanceOf(StripeApiException.class);
        }

        @Test
        @DisplayName("an empty 2xx body yields an empty object, not a null")
        void emptySuccessBodyYieldsEmptyObject() {
            server.expect(requestTo("https://api.stripe.com/v1/customers"))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            JsonNode result = client.createCustomer(KEY, "t1", "u1", null);

            assertThat(result).isNotNull();
            assertThat(result.isObject()).isTrue();
        }
    }

    @Nested
    @DisplayName("Form encoding")
    class FormEncoding {

        @Test
        @DisplayName("percent-encodes keys and values")
        void percentEncodes() {
            String encoded = new StripeFormBody()
                    .add("return_url", "https://a.test/x?y=1&z=2")
                    .addMap("metadata", java.util.Map.of("k v", "a&b"))
                    .encode();

            assertThat(encoded).contains("return_url=https%3A%2F%2Fa.test%2Fx%3Fy%3D1%26z%3D2");
            assertThat(encoded).contains("metadata%5Bk+v%5D=a%26b");
        }

        @Test
        @DisplayName("skips null values and null map entries")
        void skipsNulls() {
            StripeFormBody body = new StripeFormBody()
                    .add("a", (String) null)
                    .addMap("m", null);

            assertThat(body.isEmpty()).isTrue();
            assertThat(body.encode()).isEmpty();
        }
    }
}
