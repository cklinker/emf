package io.kelta.worker.service.billing;

import io.kelta.runtime.module.service.MemberEntitlements;
import io.kelta.worker.repository.BillingPass;
import io.kelta.worker.repository.BillingPassRepository;
import io.kelta.worker.repository.BillingPlan;
import io.kelta.worker.repository.BillingPlanRepository;
import io.kelta.worker.repository.BillingSubscription;
import io.kelta.worker.repository.BillingSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EntitlementServiceImpl Tests")
class EntitlementServiceImplTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";

    private BillingPlanRepository planRepository;
    private BillingSubscriptionRepository subscriptionRepository;
    private BillingPassRepository passRepository;
    private EntitlementServiceImpl service;

    @BeforeEach
    void setUp() {
        planRepository = mock(BillingPlanRepository.class);
        subscriptionRepository = mock(BillingSubscriptionRepository.class);
        passRepository = mock(BillingPassRepository.class);
        service = new EntitlementServiceImpl(planRepository, subscriptionRepository,
                passRepository, new ObjectMapper(), 300, 1000);

        when(subscriptionRepository.findByUserId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(passRepository.findActiveByUserId(anyString(), anyString()))
                .thenReturn(List.of());
        when(planRepository.findDefault(anyString())).thenReturn(Optional.empty());
    }

    private static BillingPlan plan(String id, String code, String kind, String entitlements) {
        return new BillingPlan(id, TENANT, code, code, kind, null, null, entitlements, null, true);
    }

    private static BillingSubscription subscription(String planId, String status) {
        return new BillingSubscription("sub-row", TENANT, USER, planId, "sub_123", "cus_123",
                status, Instant.now().plus(Duration.ofDays(10)), false, null);
    }

    private static BillingPass pass(String planId, Instant expiresAt) {
        return new BillingPass("pass-row", TENANT, USER, planId, "cs_123",
                BillingPass.STATUS_ACTIVE, Instant.now().minusSeconds(60), expiresAt);
    }

    @Nested
    @DisplayName("Plan precedence")
    class PlanPrecedence {

        @ParameterizedTest(name = "{0} subscription grants its plan")
        @ValueSource(strings = {"active", "trialing", "past_due"})
        @DisplayName("entitling statuses use the subscription plan")
        void entitlingStatusesUseSubscriptionPlan(String status) {
            when(subscriptionRepository.findByUserId(TENANT, USER))
                    .thenReturn(Optional.of(subscription("plan-paid", status)));
            when(planRepository.findById(TENANT, "plan-paid"))
                    .thenReturn(Optional.of(plan("plan-paid", "paid", "SUBSCRIPTION",
                            "{\"maxActiveWatches\":25}")));

            MemberEntitlements result = service.resolve(TENANT, USER);

            assertThat(result.planCode()).isEqualTo("paid");
            assertThat(result.subscriptionStatus()).isEqualTo(status);
            assertThat(result.intValue("maxActiveWatches", 0)).isEqualTo(25);
        }

        @ParameterizedTest(name = "{0} subscription falls back to DEFAULT")
        @ValueSource(strings = {"canceled", "unpaid", "incomplete_expired", "paused"})
        @DisplayName("non-entitling statuses lapse to the DEFAULT plan")
        void nonEntitlingStatusesLapseToDefault(String status) {
            when(subscriptionRepository.findByUserId(TENANT, USER))
                    .thenReturn(Optional.of(subscription("plan-paid", status)));
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"maxActiveWatches\":1}")));

            MemberEntitlements result = service.resolve(TENANT, USER);

            assertThat(result.planCode()).isEqualTo("free");
            // No status is reported: the baseline came from DEFAULT, not the sub.
            assertThat(result.subscriptionStatus()).isNull();
            assertThat(result.intValue("maxActiveWatches", 0)).isEqualTo(1);
        }

        @Test
        @DisplayName("member with no subscription gets the DEFAULT plan")
        void noSubscriptionUsesDefault() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"maxActiveWatches\":1}")));

            assertThat(service.resolve(TENANT, USER).planCode()).isEqualTo("free");
        }

        @Test
        @DisplayName("tenant with no DEFAULT plan resolves to empty, not an error")
        void noDefaultPlanResolvesEmpty() {
            MemberEntitlements result = service.resolve(TENANT, USER);

            assertThat(result.isEmpty()).isTrue();
            assertThat(result.intValue("maxActiveWatches", 3)).isEqualTo(3);
        }

        @Test
        @DisplayName("blank tenant or user resolves to EMPTY without touching the database")
        void blankInputsResolveEmpty() {
            assertThat(service.resolve(null, USER)).isEqualTo(MemberEntitlements.EMPTY);
            assertThat(service.resolve(TENANT, "")).isEqualTo(MemberEntitlements.EMPTY);
        }
    }

    @Nested
    @DisplayName("Pass merging")
    class PassMerging {

        @Test
        @DisplayName("numeric entitlements SUM across plan and pass")
        void numericsSum() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"maxActiveWatches\":3}")));
            when(passRepository.findActiveByUserId(TENANT, USER))
                    .thenReturn(List.of(pass("plan-pass", Instant.now().plus(Duration.ofDays(5)))));
            when(planRepository.findById(TENANT, "plan-pass"))
                    .thenReturn(Optional.of(plan("plan-pass", "boost", "ONE_TIME",
                            "{\"maxActiveWatches\":10}")));

            assertThat(service.resolve(TENANT, USER).intValue("maxActiveWatches", 0))
                    .isEqualTo(13);
        }

        @Test
        @DisplayName("boolean entitlements OR — a pass can grant but never revoke")
        void booleansOr() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"apiAccess\":false,\"smsAlerts\":true}")));
            when(passRepository.findActiveByUserId(TENANT, USER))
                    .thenReturn(List.of(pass("plan-pass", null)));
            when(planRepository.findById(TENANT, "plan-pass"))
                    .thenReturn(Optional.of(plan("plan-pass", "boost", "ONE_TIME",
                            "{\"apiAccess\":true,\"smsAlerts\":false}")));

            MemberEntitlements result = service.resolve(TENANT, USER);

            assertThat(result.boolValue("apiAccess", false)).isTrue();
            // The pass's false must not take away what the plan granted.
            assertThat(result.boolValue("smsAlerts", false)).isTrue();
        }

        @Test
        @DisplayName("array entitlements union without duplicates")
        void arraysUnion() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"channels\":[\"email\",\"push\"]}")));
            when(passRepository.findActiveByUserId(TENANT, USER))
                    .thenReturn(List.of(pass("plan-pass", null)));
            when(planRepository.findById(TENANT, "plan-pass"))
                    .thenReturn(Optional.of(plan("plan-pass", "boost", "ONE_TIME",
                            "{\"channels\":[\"push\",\"sms\"]}")));

            assertThat(service.resolve(TENANT, USER).listValue("channels"))
                    .containsExactly("email", "push", "sms");
        }

        @Test
        @DisplayName("multiple live passes all stack")
        void multiplePassesStack() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"maxActiveWatches\":1}")));
            when(passRepository.findActiveByUserId(TENANT, USER)).thenReturn(List.of(
                    pass("plan-pass", Instant.now().plus(Duration.ofDays(5))),
                    pass("plan-pass", Instant.now().plus(Duration.ofDays(9)))));
            when(planRepository.findById(TENANT, "plan-pass"))
                    .thenReturn(Optional.of(plan("plan-pass", "boost", "ONE_TIME",
                            "{\"maxActiveWatches\":10}")));

            assertThat(service.resolve(TENANT, USER).intValue("maxActiveWatches", 0))
                    .isEqualTo(21);
        }

        @Test
        @DisplayName("an expired pass is ignored even while its row still says ACTIVE")
        void expiredPassIgnoredDespiteActiveStatus() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"maxActiveWatches\":3}")));
            // Status ACTIVE but expiry in the past: the sweep has not run yet.
            when(passRepository.findActiveByUserId(TENANT, USER))
                    .thenReturn(List.of(pass("plan-pass", Instant.now().minusSeconds(60))));

            assertThat(service.resolve(TENANT, USER).intValue("maxActiveWatches", 0))
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("a not-yet-started pass is ignored")
        void futurePassIgnored() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"maxActiveWatches\":3}")));
            BillingPass future = new BillingPass("p", TENANT, USER, "plan-pass", "cs_1",
                    BillingPass.STATUS_ACTIVE,
                    Instant.now().plus(Duration.ofDays(1)),
                    Instant.now().plus(Duration.ofDays(30)));
            when(passRepository.findActiveByUserId(TENANT, USER)).thenReturn(List.of(future));

            assertThat(service.resolve(TENANT, USER).intValue("maxActiveWatches", 0))
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("a pass with no expiry never lapses")
        void openEndedPassApplies() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"maxActiveWatches\":3}")));
            when(passRepository.findActiveByUserId(TENANT, USER))
                    .thenReturn(List.of(pass("plan-pass", null)));
            when(planRepository.findById(TENANT, "plan-pass"))
                    .thenReturn(Optional.of(plan("plan-pass", "boost", "ONE_TIME",
                            "{\"maxActiveWatches\":10}")));

            assertThat(service.resolve(TENANT, USER).intValue("maxActiveWatches", 0))
                    .isEqualTo(13);
        }
    }

    @Nested
    @DisplayName("Merge semantics")
    class MergeSemantics {

        @Test
        @DisplayName("integral sums stay integral")
        void integralSumsStayIntegral() {
            Map<String, Object> base = new LinkedHashMap<>(Map.of("n", 3));
            EntitlementServiceImpl.mergeInto(base, Map.of("n", 4));

            assertThat(base.get("n")).isInstanceOf(Long.class);
            assertThat(((Number) base.get("n")).intValue()).isEqualTo(7);
        }

        @Test
        @DisplayName("fractional sums do not lose precision")
        void fractionalSumsArePrecise() {
            Map<String, Object> base = new LinkedHashMap<>(Map.of("n", 0.1));
            EntitlementServiceImpl.mergeInto(base, Map.of("n", 0.2));

            assertThat(((Number) base.get("n")).doubleValue()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("mismatched types are last-write-wins rather than an error")
        void mismatchedTypesLastWriteWins() {
            Map<String, Object> base = new LinkedHashMap<>(Map.of("k", 5));
            EntitlementServiceImpl.mergeInto(base, Map.of("k", "unlimited"));

            assertThat(base.get("k")).isEqualTo("unlimited");
        }

        @Test
        @DisplayName("keys only in the addition are added")
        void newKeysAdded() {
            Map<String, Object> base = new LinkedHashMap<>(Map.of("a", 1));
            EntitlementServiceImpl.mergeInto(base, Map.of("b", 2));

            assertThat(base).containsKeys("a", "b");
        }
    }

    @Nested
    @DisplayName("Malformed entitlement JSON")
    class MalformedJson {

        @Test
        @DisplayName("unparseable plan JSON yields no entitlements rather than throwing")
        void unparseableJsonIsIgnored() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT", "{not json")));

            assertThat(service.resolve(TENANT, USER).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("non-object JSON yields no entitlements")
        void nonObjectJsonIsIgnored() {
            assertThat(service.parseEntitlements("[1,2,3]")).isEmpty();
            assertThat(service.parseEntitlements("\"text\"")).isEmpty();
        }

        @Test
        @DisplayName("null or blank JSON yields no entitlements")
        void nullJsonIsIgnored() {
            assertThat(service.parseEntitlements(null)).isEmpty();
            assertThat(service.parseEntitlements("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Caching and invalidation")
    class Caching {

        @Test
        @DisplayName("repeat resolves are served from cache")
        void repeatResolvesAreCached() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT", "{}")));

            service.resolve(TENANT, USER);
            service.resolve(TENANT, USER);

            verify(subscriptionRepository, times(1)).findByUserId(TENANT, USER);
        }

        @Test
        @DisplayName("invalidate forces a re-read for that member only")
        void invalidateForcesReread() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT", "{}")));

            service.resolve(TENANT, USER);
            service.resolve(TENANT, "other-user");
            service.invalidate(TENANT, USER);
            service.resolve(TENANT, USER);
            service.resolve(TENANT, "other-user");

            verify(subscriptionRepository, times(2)).findByUserId(TENANT, USER);
            verify(subscriptionRepository, times(1)).findByUserId(TENANT, "other-user");
        }

        @Test
        @DisplayName("invalidateTenant clears every member of that tenant only")
        void invalidateTenantClearsWholeTenant() {
            when(planRepository.findDefault(anyString()))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT", "{}")));

            service.resolve(TENANT, USER);
            service.resolve(TENANT, "user-2");
            service.resolve("tenant-2", USER);

            service.invalidateTenant(TENANT);

            service.resolve(TENANT, USER);
            service.resolve(TENANT, "user-2");
            service.resolve("tenant-2", USER);

            verify(subscriptionRepository, times(2)).findByUserId(TENANT, USER);
            verify(subscriptionRepository, times(2)).findByUserId(TENANT, "user-2");
            verify(subscriptionRepository, times(1)).findByUserId(eq("tenant-2"), eq(USER));
        }

        @Test
        @DisplayName("null arguments to invalidate are no-ops")
        void nullInvalidateIsNoOp() {
            service.invalidate(null, USER);
            service.invalidate(TENANT, null);
            service.invalidateTenant(null);
        }
    }

    @Nested
    @DisplayName("Typed accessors")
    class TypedAccessors {

        @Test
        @DisplayName("coerce numeric strings and fall back on junk")
        void coerceDefensively() {
            MemberEntitlements e = new MemberEntitlements("p", null, Map.of(
                    "n", "42", "junk", "abc", "flag", "true", "one", "x"));

            assertThat(e.intValue("n", 0)).isEqualTo(42);
            assertThat(e.intValue("junk", 7)).isEqualTo(7);
            assertThat(e.intValue("missing", 5)).isEqualTo(5);
            assertThat(e.boolValue("flag", false)).isTrue();
            assertThat(e.boolValue("junk", false)).isFalse();
            assertThat(e.listValue("one")).containsExactly("x");
            assertThat(e.listValue("missing")).isEmpty();
        }

        @Test
        @DisplayName("service-level accessors delegate to the resolved values")
        void serviceAccessorsDelegate() {
            when(planRepository.findDefault(TENANT))
                    .thenReturn(Optional.of(plan("plan-free", "free", "DEFAULT",
                            "{\"maxActiveWatches\":4,\"apiAccess\":true,"
                                    + "\"channels\":[\"push\"]}")));

            assertThat(service.intLimit(TENANT, USER, "maxActiveWatches", 0)).isEqualTo(4);
            assertThat(service.boolLimit(TENANT, USER, "apiAccess", false)).isTrue();
            assertThat(service.listLimit(TENANT, USER, "channels")).containsExactly("push");
        }
    }
}
