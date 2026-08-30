package io.kelta.worker.listener;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.StorageConfig;
import io.kelta.runtime.query.AggregationSpec;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.FilterOperator;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.BillingEntitlementRule;
import io.kelta.worker.service.billing.BillingEntitlementRuleCache;
import io.kelta.worker.service.billing.EntitlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("MemberEntitlementQuotaHook Tests")
class MemberEntitlementQuotaHookTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String COLLECTION = "watches";

    private BillingEntitlementRuleCache ruleCache;
    private EntitlementService entitlementService;
    private CollectionRegistry collectionRegistry;
    private QueryEngine queryEngine;
    private MemberEntitlementQuotaHook hook;

    @BeforeEach
    void setUp() {
        ruleCache = mock(BillingEntitlementRuleCache.class);
        entitlementService = mock(EntitlementService.class);
        collectionRegistry = mock(CollectionRegistry.class);
        queryEngine = mock(QueryEngine.class);
        hook = new MemberEntitlementQuotaHook(ruleCache, entitlementService,
                collectionRegistry, queryEngine, new ObjectMapper());

        when(ruleCache.forCollection(anyString(), anyString())).thenReturn(List.of());
        when(collectionRegistry.get(COLLECTION)).thenReturn(collectionWithOwner());
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * A realistic collection: it does NOT declare {@code createdBy}.
     *
     * <p>No real collection does — {@code createdBy} is a system audit column every record carries.
     * An earlier version of this fixture declared it as a lookup field, which made
     * {@code hasField("createdBy")} true and hid issue #1384: in production the guard was always
     * false, so quotas failed open on every positive limit. Do not add it back.
     */
    private static CollectionDefinition collectionWithOwner() {
        return CollectionDefinition.builder()
                .name(COLLECTION)
                .displayName("Watches")
                .storageConfig(StorageConfig.physicalTable("watches"))
                .addField(FieldDefinition.string("label", 100))
                .addField(FieldDefinition.string("status", 20))
                .build();
    }

    private static BillingEntitlementRule rule(String limitKey, String countFilter,
                                               String appliesTo, String message) {
        return new BillingEntitlementRule("r1", TENANT, COLLECTION, limitKey,
                countFilter, appliesTo, message, true);
    }

    private static Map<String, Object> record(String owner) {
        Map<String, Object> record = new HashMap<>();
        record.put("label", "a watch");
        if (owner != null) {
            record.put("createdBy", owner);
        }
        return record;
    }

    private void withRule(BillingEntitlementRule r) {
        when(ruleCache.forCollection(TENANT, COLLECTION)).thenReturn(List.of(r));
    }

    private void withCount(long count) {
        when(queryEngine.aggregate(any(), any(), any()))
                .thenReturn(Map.of("memberRecordCount", count));
    }

    private void asPortalActor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Type", "PORTAL");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void asInternalActor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Type", "INTERNAL");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Nested
    @DisplayName("Registration contract")
    class RegistrationContract {

        @Test
        @DisplayName("is a wildcard hook running early in its group")
        void isWildcard() {
            assertThat(hook.getCollectionName()).isEqualTo(BeforeSaveHookRegistry.WILDCARD);
            assertThat(hook.getOrder()).isEqualTo(-90);
        }
    }

    @Nested
    @DisplayName("Fast path")
    class FastPath {

        @Test
        @DisplayName("no rule for the collection costs nothing")
        void noRuleCostsNothing() {
            asPortalActor();

            BeforeSaveResult result = hook.beforeCreate(COLLECTION, record(USER), TENANT);

            assertThat(result.isSuccess()).isTrue();
            // The whole point of the wildcard fast path: no entitlement lookup,
            // no registry hit, no query.
            verifyNoInteractions(entitlementService, queryEngine);
            verify(collectionRegistry, never()).get(anyString());
        }

        @Test
        @DisplayName("blank tenant or null collection is a no-op")
        void blankInputsAreNoOps() {
            assertThat(hook.beforeCreate(COLLECTION, record(USER), null).isSuccess()).isTrue();
            assertThat(hook.beforeCreate(COLLECTION, record(USER), "  ").isSuccess()).isTrue();
            assertThat(hook.beforeCreate(null, record(USER), TENANT).isSuccess()).isTrue();
            verifyNoInteractions(entitlementService, queryEngine);
        }

        @Test
        @DisplayName("a record with no actor is allowed (flow, scheduler, import)")
        void noActorAllowed() {
            withRule(rule("maxActiveWatches", null, "PORTAL", null));

            BeforeSaveResult result = hook.beforeCreate(COLLECTION, record(null), TENANT);

            assertThat(result.isSuccess()).isTrue();
            verifyNoInteractions(entitlementService, queryEngine);
        }

        @Test
        @DisplayName("a member with no such entitlement key is uncapped")
        void unknownEntitlementKeyIsUncapped() {
            asPortalActor();
            withRule(rule("maxActiveWatches", null, "PORTAL", null));
            when(entitlementService.intLimit(TENANT, USER, "maxActiveWatches", Integer.MAX_VALUE))
                    .thenReturn(Integer.MAX_VALUE);

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isTrue();
            verifyNoInteractions(queryEngine);
        }
    }

    @Nested
    @DisplayName("Enforcement")
    class Enforcement {

        @Test
        @DisplayName("allows a member below the limit")
        void allowsBelowLimit() {
            asPortalActor();
            withRule(rule("maxActiveWatches", null, "PORTAL", null));
            when(entitlementService.intLimit(TENANT, USER, "maxActiveWatches", Integer.MAX_VALUE))
                    .thenReturn(3);
            withCount(2);

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("rejects a member exactly at the limit")
        void rejectsAtLimit() {
            asPortalActor();
            withRule(rule("maxActiveWatches", null, "PORTAL", null));
            when(entitlementService.intLimit(TENANT, USER, "maxActiveWatches", Integer.MAX_VALUE))
                    .thenReturn(3);
            withCount(3);

            BeforeSaveResult result = hook.beforeCreate(COLLECTION, record(USER), TENANT);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).message()).contains("3/3").contains("Upgrade");
        }

        @Test
        @DisplayName("a zero limit rejects without even counting")
        void zeroLimitRejectsImmediately() {
            asPortalActor();
            withRule(rule("maxActiveWatches", null, "PORTAL", null));
            when(entitlementService.intLimit(TENANT, USER, "maxActiveWatches", Integer.MAX_VALUE))
                    .thenReturn(0);

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isFalse();
            verifyNoInteractions(queryEngine);
        }

        @Test
        @DisplayName("uses the rule's custom message when set")
        void usesCustomMessage() {
            asPortalActor();
            withRule(rule("maxActiveWatches", null, "PORTAL", "Upgrade to add more watches."));
            when(entitlementService.intLimit(TENANT, USER, "maxActiveWatches", Integer.MAX_VALUE))
                    .thenReturn(1);
            withCount(1);

            BeforeSaveResult result = hook.beforeCreate(COLLECTION, record(USER), TENANT);

            assertThat(result.getErrors().get(0).message())
                    .isEqualTo("Upgrade to add more watches.");
        }

        @Test
        @DisplayName("counts only the acting member's rows")
        void countsOnlyOwnRows() {
            asPortalActor();
            withRule(rule("maxActiveWatches", null, "PORTAL", null));
            when(entitlementService.intLimit(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(5);
            withCount(0);

            hook.beforeCreate(COLLECTION, record(USER), TENANT);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FilterCondition>> filters = ArgumentCaptor.forClass(List.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<AggregationSpec>> specs = ArgumentCaptor.forClass(List.class);
            verify(queryEngine).aggregate(any(), filters.capture(), specs.capture());

            assertThat(filters.getValue()).hasSize(1);
            assertThat(filters.getValue().get(0).fieldName()).isEqualTo("createdBy");
            assertThat(filters.getValue().get(0).operator()).isEqualTo(FilterOperator.EQ);
            assertThat(filters.getValue().get(0).value()).isEqualTo(USER);
            assertThat(specs.getValue().get(0).function()).isEqualTo("COUNT");
        }
    }

    @Nested
    @DisplayName("Actor type")
    class ActorType {

        @Test
        @DisplayName("a PORTAL rule does not constrain internal staff")
        void portalRuleSkipsInternal() {
            asInternalActor();
            withRule(rule("maxActiveWatches", null, "PORTAL", null));

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isTrue();
            verifyNoInteractions(entitlementService, queryEngine);
        }

        @Test
        @DisplayName("an ALL rule constrains internal staff too")
        void allRuleAppliesToInternal() {
            asInternalActor();
            withRule(rule("maxActiveWatches", null, "ALL", null));
            when(entitlementService.intLimit(TENANT, USER, "maxActiveWatches", Integer.MAX_VALUE))
                    .thenReturn(1);
            withCount(1);

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isFalse();
        }

        @Test
        @DisplayName("no request context is treated as internal")
        void noRequestContextIsInternal() {
            // Flow/scheduler path: no servlet request bound.
            withRule(rule("maxActiveWatches", null, "PORTAL", null));

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isTrue();
            verifyNoInteractions(entitlementService, queryEngine);
        }
    }

    @Nested
    @DisplayName("countFilter safety")
    class CountFilterSafety {

        @Test
        @DisplayName("appends validated equality predicates")
        void appendsValidatedPredicates() {
            asPortalActor();
            withRule(rule("maxActiveWatches", "{\"status\":\"ACTIVE\"}", "PORTAL", null));
            when(entitlementService.intLimit(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(5);
            withCount(0);

            hook.beforeCreate(COLLECTION, record(USER), TENANT);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FilterCondition>> filters = ArgumentCaptor.forClass(List.class);
            verify(queryEngine).aggregate(any(), filters.capture(), any());

            assertThat(filters.getValue()).hasSize(2);
            assertThat(filters.getValue().get(1).fieldName()).isEqualTo("status");
            assertThat(filters.getValue().get(1).value()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("an unknown field name is refused — never interpolated")
        void unknownFieldRefused() {
            asPortalActor();
            // The injection attempt: a field name that is not on the collection.
            withRule(rule("maxActiveWatches",
                    "{\"1=1 OR x\":\"y\"}", "PORTAL", null));
            when(entitlementService.intLimit(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(1);

            BeforeSaveResult result = hook.beforeCreate(COLLECTION, record(USER), TENANT);

            // Fails open (allows the write) rather than enforcing a wrong count,
            // and crucially never reaches the query engine.
            assertThat(result.isSuccess()).isTrue();
            verifyNoInteractions(queryEngine);
        }

        @Test
        @DisplayName("unparseable or non-object filter JSON fails open")
        void malformedFilterFailsOpen() {
            asPortalActor();
            when(entitlementService.intLimit(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(1);

            withRule(rule("k", "{not json", "PORTAL", null));
            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isTrue();

            withRule(rule("k", "[1,2,3]", "PORTAL", null));
            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isTrue();

            verifyNoInteractions(queryEngine);
        }

        @Test
        @DisplayName("preserves scalar types rather than stringifying everything")
        void preservesScalarTypes() {
            asPortalActor();
            withRule(rule("k", "{\"status\":\"A\"}", "PORTAL", null));
            when(entitlementService.intLimit(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(9);
            withCount(0);

            hook.beforeCreate(COLLECTION, record(USER), TENANT);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FilterCondition>> filters = ArgumentCaptor.forClass(List.class);
            verify(queryEngine).aggregate(any(), filters.capture(), any());
            assertThat(filters.getValue().get(1).value()).isInstanceOf(String.class);
        }
    }

    @Nested
    @DisplayName("Fail-open behaviour")
    class FailOpen {

        @Test
        @DisplayName("an unknown collection allows the write")
        void unknownCollectionAllows() {
            asPortalActor();
            withRule(rule("k", null, "PORTAL", null));
            when(entitlementService.intLimit(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(1);
            when(collectionRegistry.get(COLLECTION)).thenReturn(null);

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isTrue();
            verifyNoInteractions(queryEngine);
        }

        @Test
        @DisplayName("a collection without the owner field allows the write")
        void undeclaredOwnerFieldStillEnforces() {
            // Regression for #1384. createdBy is a system audit field, so no collection declares
            // it; the guard must use hasQueryableField, not hasField. When it used hasField this
            // returned success and never touched the query engine — quotas silently did not
            // enforce on ANY collection, for every positive limit.
            asPortalActor();
            withRule(rule("k", null, "PORTAL", null));
            when(entitlementService.intLimit(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(1);
            when(collectionRegistry.get(COLLECTION)).thenReturn(CollectionDefinition.builder()
                    .name(COLLECTION)
                    .displayName("Watches")
                    .storageConfig(StorageConfig.physicalTable("watches"))
                    .addField(FieldDefinition.string("label", 100))
                    .build());
            withCount(1);

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isFalse();
        }

        @Test
        @DisplayName("a failing count allows the write rather than blocking data entry")
        void failingCountAllows() {
            asPortalActor();
            withRule(rule("k", null, "PORTAL", null));
            when(entitlementService.intLimit(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(1);
            when(queryEngine.aggregate(any(), any(), any()))
                    .thenThrow(new IllegalStateException("db down"));

            assertThat(hook.beforeCreate(COLLECTION, record(USER), TENANT).isSuccess()).isTrue();
        }
    }
}
