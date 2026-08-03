package io.kelta.worker.service.billing;

import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.worker.repository.BillingCustomer;
import io.kelta.worker.repository.BillingCustomerRepository;
import io.kelta.worker.repository.BillingPlan;
import io.kelta.worker.repository.BillingPlanRepository;
import io.kelta.worker.service.credential.CredentialResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingCheckoutService Tests")
class BillingCheckoutServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";
    private static final String OK_URL = "https://app.example.com/done";
    private static final String CANCEL_URL = "https://app.example.com/cancel";

    private BillingPlanRepository planRepository;
    private BillingCustomerRepository customerRepository;
    private StripeApiClient stripeApiClient;
    private CredentialResolver credentialResolver;
    private BillingCheckoutService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(BillingPlanRepository.class);
        customerRepository = mock(BillingCustomerRepository.class);
        stripeApiClient = mock(StripeApiClient.class);
        credentialResolver = mock(CredentialResolver.class);
        service = new BillingCheckoutService(planRepository, customerRepository,
                stripeApiClient, credentialResolver, new ReturnUrlValidator());

        when(credentialResolver.resolve(anyString(), anyString(), any()))
                .thenReturn(credential(List.of("https://app.example.com")));
        when(customerRepository.findByUserId(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    private static ResolvedCredential credential(Object allowedOrigins) {
        return new ResolvedCredential("c1", "stripe", "stripe",
                Map.of("secretKey", "sk_test_1", "webhookSecret", "whsec_1"),
                Map.of("allowedReturnOrigins", allowedOrigins),
                Instant.now());
    }

    private static BillingPlan plan(String kind, String priceId, boolean active) {
        return new BillingPlan("p1", TENANT, "standard", "Standard", kind,
                "prod_1", priceId, "{}", null, active);
    }

    private void withPlan(BillingPlan plan) {
        when(planRepository.findByCode(TENANT, "standard")).thenReturn(Optional.of(plan));
    }

    private void stripeReturns(String url) {
        when(stripeApiClient.createCheckoutSession(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new ObjectMapper().createObjectNode().put("url", url));
    }

    @Nested
    @DisplayName("Checkout")
    class Checkout {

        @Test
        @DisplayName("returns the processor's checkout URL")
        void returnsCheckoutUrl() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));
            stripeReturns("https://checkout.test/s1");

            String url = service.createCheckoutSession(TENANT, USER, "standard", OK_URL, CANCEL_URL);

            assertThat(url).isEqualTo("https://checkout.test/s1");
        }

        @Test
        @DisplayName("maps plan kind to processor mode")
        void mapsKindToMode() {
            withPlan(plan(BillingPlan.KIND_ONE_TIME, "price_1", true));
            stripeReturns("https://checkout.test/s1");

            service.createCheckoutSession(TENANT, USER, "standard", OK_URL, CANCEL_URL);

            verify(stripeApiClient).createCheckoutSession(anyString(), eq(TENANT), eq(USER),
                    eq("payment"), eq("price_1"), eq("standard"), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("reuses an existing processor customer")
        void reusesExistingCustomer() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));
            stripeReturns("https://checkout.test/s1");
            when(customerRepository.findByUserId(TENANT, USER)).thenReturn(
                    Optional.of(new BillingCustomer("c", TENANT, USER, "cus_9", null)));

            service.createCheckoutSession(TENANT, USER, "standard", OK_URL, CANCEL_URL);

            verify(stripeApiClient).createCheckoutSession(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), eq("cus_9"), anyString(), anyString());
        }

        @Test
        @DisplayName("404 for an unknown or inactive plan")
        void unknownOrInactivePlan() {
            when(planRepository.findByCode(TENANT, "standard")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", false));
            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("the DEFAULT plan is not purchasable")
        void defaultPlanNotPurchasable() {
            withPlan(plan(BillingPlan.KIND_DEFAULT, "price_1", true));

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        @DisplayName("a plan with no price id is a configuration conflict, not a member error")
        void planWithoutPriceId() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, null, true));

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    @Nested
    @DisplayName("Return-URL enforcement")
    class ReturnUrlEnforcement {

        @Test
        @DisplayName("rejects a success URL outside the allowed origins — before calling out")
        void rejectsForeignSuccessUrl() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", "https://evil.test/steal", CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

            // The open redirect never reaches the processor.
            verify(stripeApiClient, never()).createCheckoutSession(anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("rejects a foreign cancel URL too")
        void rejectsForeignCancelUrl() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, "https://evil.test/x"))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        @DisplayName("a tenant with no configured origins cannot redirect anywhere")
        void emptyAllowlistDeniesAll() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));
            when(credentialResolver.resolve(anyString(), anyString(), any()))
                    .thenReturn(credential(List.of()));

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        @DisplayName("accepts a comma-separated origins string as well as a list")
        void acceptsCommaSeparatedOrigins() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));
            when(credentialResolver.resolve(anyString(), anyString(), any()))
                    .thenReturn(credential("https://other.test, https://app.example.com"));
            stripeReturns("https://checkout.test/s1");

            assertThat(service.createCheckoutSession(TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isEqualTo("https://checkout.test/s1");
        }
    }

    @Nested
    @DisplayName("Portal session")
    class PortalSession {

        @Test
        @DisplayName("returns the portal URL for a member with a billing account")
        void returnsPortalUrl() {
            when(customerRepository.findByUserId(TENANT, USER)).thenReturn(
                    Optional.of(new BillingCustomer("c", TENANT, USER, "cus_1", null)));
            when(stripeApiClient.createBillingPortalSession(anyString(), anyString(), anyString()))
                    .thenReturn(new ObjectMapper().createObjectNode()
                            .put("url", "https://portal.test/p1"));

            assertThat(service.createPortalSession(TENANT, USER, OK_URL))
                    .isEqualTo("https://portal.test/p1");
        }

        @Test
        @DisplayName("409 when the member has never transacted")
        void noCustomerYet() {
            assertThatThrownBy(() -> service.createPortalSession(TENANT, USER, OK_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("rejects a foreign return URL")
        void rejectsForeignReturnUrl() {
            when(customerRepository.findByUserId(TENANT, USER)).thenReturn(
                    Optional.of(new BillingCustomer("c", TENANT, USER, "cus_1", null)));

            assertThatThrownBy(() -> service.createPortalSession(TENANT, USER, "https://evil.test/x"))
                    .isInstanceOf(ResponseStatusException.class);
            verify(stripeApiClient, never())
                    .createBillingPortalSession(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Failure mapping")
    class FailureMapping {

        @Test
        @DisplayName("a missing credential is a tenant configuration conflict")
        void missingCredential() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));
            when(credentialResolver.resolve(anyString(), anyString(), any()))
                    .thenThrow(new IllegalStateException("not found"));

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("a bad processor key is a tenant problem (409), not a bad gateway")
        void authFailureIsConflict() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));
            when(stripeApiClient.createCheckoutSession(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), any(), anyString(), anyString()))
                    .thenThrow(new StripeApiException(401, "invalid_request_error", null, "bad key"));

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("any other processor error is 502 and never leaks its message")
        void otherProcessorErrorIsBadGateway() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));
            when(stripeApiClient.createCheckoutSession(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), any(), anyString(), anyString()))
                    .thenThrow(new StripeApiException(400, "invalid_request_error",
                            "resource_missing", "No such price: price_secret_internal"));

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                        // The processor's message can name account internals.
                        assertThat(e.getReason()).doesNotContain("price_secret_internal");
                    });
        }

        @Test
        @DisplayName("a response with no URL is a bad gateway, not a silent success")
        void missingUrlIsBadGateway() {
            withPlan(plan(BillingPlan.KIND_SUBSCRIPTION, "price_1", true));
            when(stripeApiClient.createCheckoutSession(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), any(), anyString(), anyString()))
                    .thenReturn(new ObjectMapper().createObjectNode());

            assertThatThrownBy(() -> service.createCheckoutSession(
                    TENANT, USER, "standard", OK_URL, CANCEL_URL))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        }
    }
}
