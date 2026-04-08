package io.kelta.worker.service;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CheckResourcesRequestBuilder;
import dev.cerbos.sdk.CheckResourcesResult;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import io.kelta.worker.config.WorkerProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CerbosAuthorizationService (Worker)")
class CerbosAuthorizationServiceTest {

    @Mock
    private CerbosBlockingClient cerbosClient;

    @Mock(answer = org.mockito.Answers.RETURNS_SELF)
    private CheckResourcesRequestBuilder batchRequest;

    @Mock
    private CheckResourcesResult batchResult;

    @Mock
    private CheckResult fieldCheckResult;

    private CerbosAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new CerbosAuthorizationService(cerbosClient, new SimpleMeterRegistry(), new WorkerProperties());
    }

    @Nested
    @DisplayName("Field access caching")
    class FieldAccessCaching {

        @Test
        @DisplayName("Should cache field access results and serve from cache on repeat calls")
        void cachesFieldAccess() throws Exception {
            when(cerbosClient.batch(any(Principal.class))).thenReturn(batchRequest);
            when(batchRequest.check()).thenReturn(batchResult);
            when(batchResult.find("field-1")).thenReturn(Optional.of(fieldCheckResult));
            when(batchResult.find("field-2")).thenReturn(Optional.of(fieldCheckResult));
            when(fieldCheckResult.isAllowed("read")).thenReturn(true);

            List<String> fields = List.of("field-1", "field-2");

            // First call — cache miss
            List<String> result1 = service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1", fields, "read");
            assertThat(result1).containsExactly("field-1", "field-2");

            // Second call — cache hit
            List<String> result2 = service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1", fields, "read");
            assertThat(result2).containsExactly("field-1", "field-2");

            // Cerbos should only be called once
            verify(cerbosClient, times(1)).batch(any());
        }

        @Test
        @DisplayName("Should serve subset of cached allowed fields when checking fewer fields")
        void servesSubsetFromCache() throws Exception {
            when(cerbosClient.batch(any(Principal.class))).thenReturn(batchRequest);
            when(batchRequest.check()).thenReturn(batchResult);
            when(batchResult.find("field-1")).thenReturn(Optional.of(fieldCheckResult));
            when(batchResult.find("field-2")).thenReturn(Optional.of(fieldCheckResult));
            when(batchResult.find("field-3")).thenReturn(Optional.of(fieldCheckResult));
            when(fieldCheckResult.isAllowed("read")).thenReturn(true, true, false);

            // First call with all fields
            List<String> result1 = service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1",
                    List.of("field-1", "field-2", "field-3"), "read");
            assertThat(result1).containsExactly("field-1", "field-2");

            // Second call with subset — should filter from cache
            List<String> result2 = service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1",
                    List.of("field-1", "field-3"), "read");
            assertThat(result2).containsExactly("field-1");

            verify(cerbosClient, times(1)).batch(any());
        }

        @Test
        @DisplayName("Should use separate cache entries for different collections")
        void separatesByCollection() throws Exception {
            when(cerbosClient.batch(any(Principal.class))).thenReturn(batchRequest);
            when(batchRequest.check()).thenReturn(batchResult);
            when(batchResult.find("field-1")).thenReturn(Optional.of(fieldCheckResult));
            when(fieldCheckResult.isAllowed("read")).thenReturn(true);

            List<String> fields = List.of("field-1");

            service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1", fields, "read");
            service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-2", fields, "read");

            // Two different collections = two Cerbos calls
            verify(cerbosClient, times(2)).batch(any());
        }
    }

    @Nested
    @DisplayName("Cache eviction")
    class CacheEviction {

        @Test
        @DisplayName("Should evict cached field access for tenant and re-check")
        void evictsForTenant() throws Exception {
            when(cerbosClient.batch(any(Principal.class))).thenReturn(batchRequest);
            when(batchRequest.check()).thenReturn(batchResult);
            when(batchResult.find("field-1")).thenReturn(Optional.of(fieldCheckResult));
            when(fieldCheckResult.isAllowed("read")).thenReturn(true);

            List<String> fields = List.of("field-1");

            // Populate cache
            service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1", fields, "read");

            // Evict
            service.evictForTenant("tenant-1");

            // Should call Cerbos again
            service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1", fields, "read");

            verify(cerbosClient, times(2)).batch(any());
        }

        @Test
        @DisplayName("Should not evict entries for other tenants")
        void doesNotEvictOtherTenants() throws Exception {
            when(cerbosClient.batch(any(Principal.class))).thenReturn(batchRequest);
            when(batchRequest.check()).thenReturn(batchResult);
            when(batchResult.find("field-1")).thenReturn(Optional.of(fieldCheckResult));
            when(fieldCheckResult.isAllowed("read")).thenReturn(true);

            List<String> fields = List.of("field-1");

            // Populate cache for tenant-1
            service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1", fields, "read");

            // Evict for different tenant
            service.evictForTenant("tenant-other");

            // Should still serve from cache
            service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1", fields, "read");

            verify(cerbosClient, times(1)).batch(any());
        }
    }

    @Nested
    @DisplayName("Empty and edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should return empty list for empty field list")
        void emptyFieldList() {
            List<String> result = service.batchCheckFieldAccess(
                    "user@test.com", "profile-1", "tenant-1", "col-1", List.of(), "read");
            assertThat(result).isEmpty();
            verifyNoInteractions(cerbosClient);
        }
    }
}
