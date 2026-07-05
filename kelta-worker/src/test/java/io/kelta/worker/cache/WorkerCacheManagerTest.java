package io.kelta.worker.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerCacheManagerTest {

    private WorkerCacheManager cacheManager;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cacheManager = new WorkerCacheManager(meterRegistry);
    }

    // ── Custom Domain Cache ──────────────────────────────────────────────

    @Test
    void getCustomDomain_returnsEmptyWhenNotCached() {
        assertThat(cacheManager.getCustomDomain("app.acme.com")).isEmpty();
    }

    @Test
    void putAndGetCustomDomain_returnsCachedValue() {
        cacheManager.putCustomDomain("app.acme.com", "acme");

        Optional<String> result = cacheManager.getCustomDomain("app.acme.com");
        assertThat(result).isPresent().contains("acme");
    }

    @Test
    void putCustomDomainNotFound_cachesNegativeResult() {
        cacheManager.putCustomDomainNotFound("unknown.com");

        Optional<String> result = cacheManager.getCustomDomain("unknown.com");
        assertThat(result).isPresent().contains(WorkerCacheManager.DOMAIN_NOT_FOUND);
    }

    @Test
    void evictCustomDomain_removesCachedEntry() {
        cacheManager.putCustomDomain("app.acme.com", "acme");
        cacheManager.evictCustomDomain("app.acme.com");

        assertThat(cacheManager.getCustomDomain("app.acme.com")).isEmpty();
    }

    @Test
    void evictAllCustomDomains_clearsAllEntries() {
        cacheManager.putCustomDomain("app.acme.com", "acme");
        cacheManager.putCustomDomain("app.beta.com", "beta");
        cacheManager.evictAllCustomDomains();

        assertThat(cacheManager.getCustomDomain("app.acme.com")).isEmpty();
        assertThat(cacheManager.getCustomDomain("app.beta.com")).isEmpty();
    }

    // ── User Permissions Cache ───────────────────────────────────────────

    @Test
    void getPermissions_returnsEmptyWhenNotCached() {
        assertThat(cacheManager.getPermissions("profile-123")).isEmpty();
    }

    @Test
    void putAndGetPermissions_returnsCachedValue() {
        Map<String, Object> permissions = Map.of(
                "systemPermissions", Map.of("manage_users", true),
                "objectPermissions", Map.of(),
                "fieldPermissions", Map.of()
        );

        cacheManager.putPermissions("profile-123", permissions);

        Optional<Map<String, Object>> result = cacheManager.getPermissions("profile-123");
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey("systemPermissions");
    }

    @Test
    void evictPermissions_removesCachedEntry() {
        cacheManager.putPermissions("profile-123", Map.of("systemPermissions", Map.of()));
        cacheManager.evictPermissions("profile-123");

        assertThat(cacheManager.getPermissions("profile-123")).isEmpty();
    }

    @Test
    void evictAllPermissions_clearsAllEntries() {
        cacheManager.putPermissions("profile-1", Map.of("systemPermissions", Map.of()));
        cacheManager.putPermissions("profile-2", Map.of("systemPermissions", Map.of()));
        cacheManager.evictAllPermissions();

        assertThat(cacheManager.getPermissions("profile-1")).isEmpty();
        assertThat(cacheManager.getPermissions("profile-2")).isEmpty();
    }

    // ── Tenant Limits Cache ──────────────────────────────────────────────

    @Test
    void getTenantLimits_returnsEmptyWhenNotCached() {
        assertThat(cacheManager.getTenantLimits("tenant-abc")).isEmpty();
    }

    @Test
    void putAndGetTenantLimits_returnsCachedValue() {
        Map<String, Object> limits = Map.of(
                "apiCallsPerDay", 50000,
                "maxUsers", 200
        );

        cacheManager.putTenantLimits("tenant-abc", limits);

        Optional<Map<String, Object>> result = cacheManager.getTenantLimits("tenant-abc");
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry("apiCallsPerDay", 50000);
    }

    @Test
    void evictTenantLimits_removesCachedEntry() {
        cacheManager.putTenantLimits("tenant-abc", Map.of("apiCallsPerDay", 50000));
        cacheManager.evictTenantLimits("tenant-abc");

        assertThat(cacheManager.getTenantLimits("tenant-abc")).isEmpty();
    }

    // ── System Collection Cache ───────────────────────────────────────────

    @Test
    void getSystemCollectionResponse_returnsEmptyWhenNotCached() {
        assertThat(cacheManager.getSystemCollectionResponse("t1:collections:list:q1")).isEmpty();
    }

    @Test
    void putAndGetSystemCollectionResponse_returnsCachedValue() {
        Map<String, Object> response = Map.of("data", java.util.List.of());
        cacheManager.putSystemCollectionResponse("t1:collections:list:q1", response);

        Optional<Map<String, Object>> result = cacheManager.getSystemCollectionResponse("t1:collections:list:q1");
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey("data");
    }

    @Test
    void evictSystemCollection_removesMatchingEntries() {
        cacheManager.putSystemCollectionResponse("t1:collections:list:q1", Map.of("data", java.util.List.of()));
        cacheManager.putSystemCollectionResponse("t1:collections:id:abc", Map.of("data", Map.of()));
        cacheManager.putSystemCollectionResponse("t1:ui-pages:list:q1", Map.of("data", java.util.List.of()));

        cacheManager.evictSystemCollection("t1", "collections");

        assertThat(cacheManager.getSystemCollectionResponse("t1:collections:list:q1")).isEmpty();
        assertThat(cacheManager.getSystemCollectionResponse("t1:collections:id:abc")).isEmpty();
        // ui-pages should not be affected
        assertThat(cacheManager.getSystemCollectionResponse("t1:ui-pages:list:q1")).isPresent();
    }

    @Test
    void evictAllSystemCollections_clearsAllEntries() {
        cacheManager.putSystemCollectionResponse("t1:collections:list:q1", Map.of("data", java.util.List.of()));
        cacheManager.putSystemCollectionResponse("t2:ui-pages:list:q1", Map.of("data", java.util.List.of()));

        cacheManager.evictAllSystemCollections();

        assertThat(cacheManager.getSystemCollectionResponse("t1:collections:list:q1")).isEmpty();
        assertThat(cacheManager.getSystemCollectionResponse("t2:ui-pages:list:q1")).isEmpty();
    }

    @Test
    void evictSystemCollection_handlesNullTenantId() {
        cacheManager.putSystemCollectionResponse("_:collections:list:q1", Map.of("data", java.util.List.of()));

        cacheManager.evictSystemCollection(null, "collections");

        assertThat(cacheManager.getSystemCollectionResponse("_:collections:list:q1")).isEmpty();
    }

    // ── System Collection Cache — deep-copy isolation ──────────────────────

    @Nested
    @DisplayName("System collection response cache deep-copies (per-request advice isolation)")
    class SystemCollectionDeepCopy {

        /**
         * Builds a nested JSON:API-shaped response: {@code data} is a list of record maps,
         * each with a mutable nested {@code attributes} map. This is the exact shape the
         * per-request field-security / masking advices mutate in place.
         */
        private Map<String, Object> nestedResponse(String ssn) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("name", "John");
            attributes.put("ssn", ssn);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("type", "contacts");
            record.put("id", "1");
            record.put("attributes", attributes);

            List<Object> data = new ArrayList<>();
            data.add(record);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            return response;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> firstRecordAttributes(Map<String, Object> response) {
            List<Object> data = (List<Object>) response.get("data");
            Map<String, Object> record = (Map<String, Object>) data.get(0);
            return (Map<String, Object>) record.get("attributes");
        }

        @Test
        @DisplayName("Copy-on-put: mutating the source map after put does not corrupt the cached value")
        void copyOnPut_sourceMutationDoesNotLeakIntoCache() {
            Map<String, Object> source = nestedResponse("123-45-6789");
            cacheManager.putSystemCollectionResponse("t1:contacts:id:1", source);

            // Mutate the exact object we handed to put(), at the nested attributes level.
            firstRecordAttributes(source).put("ssn", "MUTATED");
            firstRecordAttributes(source).put("injected", "leak");

            Optional<Map<String, Object>> cached =
                    cacheManager.getSystemCollectionResponse("t1:contacts:id:1");
            assertThat(cached).isPresent();
            Map<String, Object> cachedAttrs = firstRecordAttributes(cached.get());
            // The cached snapshot reflects the ORIGINAL values, not the post-put mutation.
            assertThat(cachedAttrs.get("ssn")).isEqualTo("123-45-6789");
            assertThat(cachedAttrs).doesNotContainKey("injected");
        }

        @Test
        @DisplayName("Copy-on-get: mutating a returned map's nested attributes does not affect a later get")
        void copyOnGet_returnedMutationDoesNotLeakIntoCache() {
            cacheManager.putSystemCollectionResponse(
                    "t1:contacts:id:1", nestedResponse("123-45-6789"));

            // First reader mutates the returned response as the advice would (in-place redaction).
            Map<String, Object> firstGet =
                    cacheManager.getSystemCollectionResponse("t1:contacts:id:1").orElseThrow();
            firstRecordAttributes(firstGet).put("ssn", "***-**-6789");
            firstRecordAttributes(firstGet).remove("name");

            // A second reader must see the pristine cached values — deep independence at the
            // nested attributes level, not just the top map.
            Map<String, Object> secondGet =
                    cacheManager.getSystemCollectionResponse("t1:contacts:id:1").orElseThrow();
            Map<String, Object> secondAttrs = firstRecordAttributes(secondGet);
            assertThat(secondAttrs.get("ssn")).isEqualTo("123-45-6789");
            assertThat(secondAttrs.get("name")).isEqualTo("John");

            // And the two gets are distinct object graphs, not the same shared reference.
            assertThat(secondGet).isNotSameAs(firstGet);
            assertThat(firstRecordAttributes(secondGet)).isNotSameAs(firstRecordAttributes(firstGet));
        }
    }

    // ── Metrics ──────────────────────────────────────────────────────────

    @Test
    void metricsAreRegistered() {
        assertThat(meterRegistry.find("worker.cache.size.custom-domain").gauge()).isNotNull();
        assertThat(meterRegistry.find("worker.cache.size.permissions").gauge()).isNotNull();
        assertThat(meterRegistry.find("worker.cache.size.tenant-limits").gauge()).isNotNull();
        assertThat(meterRegistry.find("worker.cache.size.system-collection").gauge()).isNotNull();
    }

    // ── Diagnostics ──────────────────────────────────────────────────────

    @Test
    void getCacheSummary_returnsFormattedString() {
        String summary = cacheManager.getCacheSummary();
        assertThat(summary).startsWith("WorkerCaches[");
        assertThat(summary).contains("sysCollections=");
    }
}
