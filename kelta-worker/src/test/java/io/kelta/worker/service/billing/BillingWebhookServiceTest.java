package io.kelta.worker.service.billing;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.worker.repository.BillingCustomer;
import io.kelta.worker.repository.BillingCustomerRepository;
import io.kelta.worker.repository.BillingPassRepository;
import io.kelta.worker.repository.BillingPlan;
import io.kelta.worker.repository.BillingPlanRepository;
import io.kelta.worker.repository.BillingSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingWebhookService Tests")
class BillingWebhookServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";

    private JdbcTemplate jdbcTemplate;
    private BillingCustomerRepository customerRepository;
    private BillingSubscriptionRepository subscriptionRepository;
    private BillingPassRepository passRepository;
    private BillingPlanRepository planRepository;
    private EntitlementService entitlementService;
    private PlatformEventPublisher eventPublisher;
    private BillingWebhookService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        customerRepository = mock(BillingCustomerRepository.class);
        subscriptionRepository = mock(BillingSubscriptionRepository.class);
        passRepository = mock(BillingPassRepository.class);
        planRepository = mock(BillingPlanRepository.class);
        entitlementService = mock(EntitlementService.class);
        eventPublisher = mock(PlatformEventPublisher.class);

        service = new BillingWebhookService(jdbcTemplate, customerRepository,
                subscriptionRepository, passRepository, planRepository,
                entitlementService, eventPublisher, new ObjectMapper());

        // Default: the claim succeeds (event not seen before).
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);
        when(customerRepository.findByStripeCustomerId(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    private static BillingPlan plan(String id, String code, Integer durationDays) {
        return new BillingPlan(id, TENANT, code, code, "ONE_TIME", null, null,
                "{}", durationDays, true);
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("a duplicate event is skipped without mutating anything")
        void duplicateEventIsSkipped() {
            // Claim insert affects 0 rows => already processed.
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(0);

            boolean applied = service.process(TENANT, """
                    {"id":"evt_1","type":"checkout.session.completed",
                     "data":{"object":{"id":"cs_1","mode":"payment","client_reference_id":"user-1"}}}
                    """);

            assertThat(applied).isFalse();
            verify(passRepository, never()).grant(anyString(), anyString(), anyString(),
                    anyString(), anyString(), any(), any());
            verify(customerRepository, never()).upsert(anyString(), anyString(), anyString(), anyString());
            verify(eventPublisher, never()).publish(anyString(), any());
        }

        @Test
        @DisplayName("the claim is attempted before any handler runs")
        void claimHappensFirst() {
            service.process(TENANT, """
                    {"id":"evt_1","type":"unhandled.event.type","data":{"object":{}}}
                    """);

            verify(jdbcTemplate).update(anyString(), eq("evt_1"), eq(TENANT),
                    eq("unhandled.event.type"));
        }
    }

    @Nested
    @DisplayName("Malformed input")
    class MalformedInput {

        @Test
        @DisplayName("an unparseable body is accepted and never retried")
        void unparseableBodyIsNotRetried() {
            assertThat(service.process(TENANT, "{not json")).isTrue();
            verify(jdbcTemplate, never()).update(anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("an event missing id or type is accepted and ignored")
        void missingIdOrTypeIsIgnored() {
            assertThat(service.process(TENANT, "{\"type\":\"invoice.paid\"}")).isTrue();
            assertThat(service.process(TENANT, "{\"id\":\"evt_1\"}")).isTrue();
            verify(jdbcTemplate, never()).update(anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("an unknown event type is claimed then ignored")
        void unknownEventTypeIsClaimedAndIgnored() {
            assertThat(service.process(TENANT, """
                    {"id":"evt_9","type":"radar.early_fraud_warning.created","data":{"object":{}}}
                    """)).isTrue();

            verify(jdbcTemplate).update(anyString(), eq("evt_9"), eq(TENANT), anyString());
            verify(eventPublisher, never()).publish(anyString(), any());
        }
    }

    @Nested
    @DisplayName("checkout.session.completed")
    class CheckoutCompleted {

        @Test
        @DisplayName("payment mode grants a pass and records the customer")
        void paymentModeGrantsPass() {
            when(planRepository.findByCode(TENANT, "pass-30d"))
                    .thenReturn(Optional.of(plan("plan-pass", "pass-30d", 30)));
            when(passRepository.grant(anyString(), anyString(), anyString(), anyString(),
                    any(), any(), any())).thenReturn(true);

            service.process(TENANT, """
                    {"id":"evt_1","type":"checkout.session.completed","data":{"object":{
                      "id":"cs_1","mode":"payment","customer":"cus_1",
                      "payment_intent":"pi_1","client_reference_id":"user-1",
                      "customer_details":{"email":"m@example.com"},
                      "metadata":{"planCode":"pass-30d"}}}}
                    """);

            verify(customerRepository).upsert(TENANT, USER, "cus_1", "m@example.com");

            ArgumentCaptor<Instant> expires = ArgumentCaptor.forClass(Instant.class);
            verify(passRepository).grant(eq(TENANT), eq(USER), eq("plan-pass"), eq("cs_1"),
                    eq("pi_1"), any(), expires.capture());
            // 30-day plan => expiry roughly a month out, never null.
            assertThat(expires.getValue()).isAfter(Instant.now().plusSeconds(29 * 86400));

            verify(entitlementService).invalidate(TENANT, USER);
            verify(eventPublisher).publish(
                    eq("kelta.billing.entitlement.changed." + TENANT + "." + USER), any());
        }

        @Test
        @DisplayName("subscription mode records the customer but grants no pass")
        void subscriptionModeGrantsNoPass() {
            service.process(TENANT, """
                    {"id":"evt_2","type":"checkout.session.completed","data":{"object":{
                      "id":"cs_2","mode":"subscription","customer":"cus_2",
                      "client_reference_id":"user-1"}}}
                    """);

            verify(customerRepository).upsert(TENANT, USER, "cus_2", null);
            verify(passRepository, never()).grant(anyString(), anyString(), anyString(),
                    anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("an unknown plan code grants nothing rather than a bad pass")
        void unknownPlanCodeGrantsNothing() {
            when(planRepository.findByCode(anyString(), anyString())).thenReturn(Optional.empty());

            service.process(TENANT, """
                    {"id":"evt_3","type":"checkout.session.completed","data":{"object":{
                      "id":"cs_3","mode":"payment","customer":"cus_3",
                      "client_reference_id":"user-1","metadata":{"planCode":"nope"}}}}
                    """);

            verify(passRepository, never()).grant(anyString(), anyString(), anyString(),
                    anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("a re-granted pass publishes nothing (grant returned false)")
        void regrantedPassPublishesNothing() {
            when(planRepository.findByCode(TENANT, "pass-30d"))
                    .thenReturn(Optional.of(plan("plan-pass", "pass-30d", 30)));
            when(passRepository.grant(anyString(), anyString(), anyString(), anyString(),
                    any(), any(), any())).thenReturn(false);

            service.process(TENANT, """
                    {"id":"evt_4","type":"checkout.session.completed","data":{"object":{
                      "id":"cs_4","mode":"payment","customer":"cus_4",
                      "client_reference_id":"user-1","metadata":{"planCode":"pass-30d"}}}}
                    """);

            verify(eventPublisher, never()).publish(anyString(), any());
        }

        @Test
        @DisplayName("a plan with no duration grants an open-ended pass")
        void nullDurationGrantsOpenEndedPass() {
            when(planRepository.findByCode(TENANT, "forever"))
                    .thenReturn(Optional.of(plan("plan-forever", "forever", null)));

            service.process(TENANT, """
                    {"id":"evt_5","type":"checkout.session.completed","data":{"object":{
                      "id":"cs_5","mode":"payment","customer":"cus_5",
                      "client_reference_id":"user-1","metadata":{"planCode":"forever"}}}}
                    """);

            verify(passRepository).grant(eq(TENANT), eq(USER), eq("plan-forever"), eq("cs_5"),
                    any(), any(), eq(null));
        }

        @Test
        @DisplayName("no resolvable member means nothing is written")
        void unresolvableMemberWritesNothing() {
            service.process(TENANT, """
                    {"id":"evt_6","type":"checkout.session.completed","data":{"object":{
                      "id":"cs_6","mode":"payment","customer":"cus_unknown"}}}
                    """);

            verify(customerRepository, never()).upsert(anyString(), anyString(), anyString(), anyString());
            verify(passRepository, never()).grant(anyString(), anyString(), anyString(),
                    anyString(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Member resolution")
    class MemberResolution {

        @Test
        @DisplayName("falls back to metadata.userId when no client_reference_id")
        void fallsBackToMetadataUserId() {
            when(planRepository.findByCode(TENANT, "p")).thenReturn(Optional.of(plan("pid", "p", 7)));

            service.process(TENANT, """
                    {"id":"evt_7","type":"checkout.session.completed","data":{"object":{
                      "id":"cs_7","mode":"payment","customer":"cus_7",
                      "metadata":{"userId":"user-1","planCode":"p"}}}}
                    """);

            verify(customerRepository).upsert(TENANT, USER, "cus_7", null);
        }

        @Test
        @DisplayName("falls back to the stored customer mapping last")
        void fallsBackToStoredCustomer() {
            when(customerRepository.findByStripeCustomerId(TENANT, "cus_8"))
                    .thenReturn(Optional.of(new BillingCustomer("c", TENANT, USER, "cus_8", null)));
            when(planRepository.findByCode(TENANT, "p")).thenReturn(Optional.of(plan("pid", "p", 7)));

            service.process(TENANT, """
                    {"id":"evt_8","type":"checkout.session.completed","data":{"object":{
                      "id":"cs_8","mode":"payment","customer":"cus_8",
                      "metadata":{"planCode":"p"}}}}
                    """);

            verify(passRepository).grant(eq(TENANT), eq(USER), eq("pid"), eq("cs_8"),
                    any(), any(), any());
        }
    }

    @Nested
    @DisplayName("customer.subscription.*")
    class SubscriptionEvents {

        @Test
        @DisplayName("an update mirrors status and period, and maps the price to a plan")
        void updateMirrorsSubscription() {
            when(planRepository.findByStripePriceId(TENANT, "price_1"))
                    .thenReturn(Optional.of(plan("plan-paid", "paid", null)));
            when(planRepository.findById(TENANT, "plan-paid"))
                    .thenReturn(Optional.of(plan("plan-paid", "paid", null)));

            service.process(TENANT, """
                    {"id":"evt_10","type":"customer.subscription.updated","data":{"object":{
                      "id":"sub_1","customer":"cus_1","status":"active",
                      "current_period_end":1893456000,"cancel_at_period_end":true,
                      "metadata":{"userId":"user-1"},
                      "items":{"data":[{"price":{"id":"price_1"}}]}}}}
                    """);

            verify(subscriptionRepository).upsert(eq(TENANT), eq(USER), eq("plan-paid"),
                    eq("sub_1"), eq("cus_1"), eq("active"),
                    eq(Instant.ofEpochSecond(1893456000L)), eq(true), eq(null));
            verify(entitlementService).invalidate(TENANT, USER);
        }

        @Test
        @DisplayName("an unmapped price still mirrors the subscription with a null plan")
        void unmappedPriceStillMirrors() {
            when(planRepository.findByStripePriceId(anyString(), any()))
                    .thenReturn(Optional.empty());

            service.process(TENANT, """
                    {"id":"evt_11","type":"customer.subscription.created","data":{"object":{
                      "id":"sub_2","customer":"cus_2","status":"trialing",
                      "metadata":{"userId":"user-1"},
                      "items":{"data":[{"price":{"id":"price_unknown"}}]}}}}
                    """);

            verify(subscriptionRepository).upsert(eq(TENANT), eq(USER), eq(null), eq("sub_2"),
                    eq("cus_2"), eq("trialing"), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("a deletion marks the subscription canceled and invalidates")
        void deletionMarksCanceled() {
            service.process(TENANT, """
                    {"id":"evt_12","type":"customer.subscription.deleted","data":{"object":{
                      "id":"sub_3","customer":"cus_3","status":"canceled",
                      "canceled_at":1893456000,"metadata":{"userId":"user-1"}}}}
                    """);

            verify(subscriptionRepository).markCanceled(TENANT, "sub_3", "canceled",
                    Instant.ofEpochSecond(1893456000L));
            verify(entitlementService).invalidate(TENANT, USER);
        }

        @Test
        @DisplayName("a subscription event with no resolvable member is skipped")
        void unresolvableSubscriptionMemberSkipped() {
            service.process(TENANT, """
                    {"id":"evt_13","type":"customer.subscription.updated","data":{"object":{
                      "id":"sub_4","customer":"cus_unknown","status":"active",
                      "items":{"data":[{"price":{"id":"price_1"}}]}}}}
                    """);

            verify(subscriptionRepository, never()).upsert(anyString(), anyString(), any(),
                    anyString(), any(), anyString(), any(), anyBoolean(), any());
        }
    }

    @Nested
    @DisplayName("Flow trigger bridge")
    class FlowBridge {

        @Test
        @DisplayName("invoice events bridge to the trigger namespace only")
        void invoiceEventsBridgeOnly() {
            service.process(TENANT, """
                    {"id":"evt_20","type":"invoice.payment_failed","data":{"object":{
                      "id":"in_1","customer":"cus_1","status":"open"}}}
                    """);

            ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
            verify(eventPublisher).publish(subject.capture(), any(PlatformEvent.class));
            assertThat(subject.getValue())
                    .isEqualTo("kelta.trigger." + TENANT + ".billing.subscription");

            verify(subscriptionRepository, never()).upsert(anyString(), anyString(), any(),
                    anyString(), any(), anyString(), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("a subscription change publishes both invalidation and trigger events")
        void subscriptionChangePublishesBoth() {
            when(planRepository.findByStripePriceId(anyString(), any()))
                    .thenReturn(Optional.empty());

            service.process(TENANT, """
                    {"id":"evt_21","type":"customer.subscription.updated","data":{"object":{
                      "id":"sub_5","customer":"cus_5","status":"past_due",
                      "metadata":{"userId":"user-1"},
                      "items":{"data":[{"price":{"id":"p"}}]}}}}
                    """);

            verify(eventPublisher, times(2)).publish(anyString(), any(PlatformEvent.class));
            verify(eventPublisher).publish(
                    eq("kelta.billing.entitlement.changed." + TENANT + "." + USER), any());
            verify(eventPublisher).publish(
                    eq("kelta.trigger." + TENANT + ".billing.subscription"), any());
        }
    }
}
