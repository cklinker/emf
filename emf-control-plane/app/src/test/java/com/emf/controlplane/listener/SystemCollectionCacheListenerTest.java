package com.emf.controlplane.listener;

import com.emf.controlplane.config.CacheConfig;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SystemCollectionCacheListener}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemCollectionCacheListener Tests")
class SystemCollectionCacheListenerTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache collectionsCache;

    @Mock
    private Cache collectionsListCache;

    @Mock
    private Cache permissionsCache;

    @Mock
    private Cache userIdCache;

    @Mock
    private Cache workflowRulesCache;

    @Mock
    private Cache governorLimitsCache;

    @Mock
    private Cache layoutsCache;

    @Mock
    private Cache bootstrapCache;

    private ObjectMapper objectMapper;
    private SystemCollectionCacheListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new SystemCollectionCacheListener(cacheManager, objectMapper);
    }

    private void setupCacheMocks() {
        lenient().when(cacheManager.getCache(CacheConfig.COLLECTIONS_CACHE)).thenReturn(collectionsCache);
        lenient().when(cacheManager.getCache(CacheConfig.COLLECTIONS_LIST_CACHE)).thenReturn(collectionsListCache);
        lenient().when(cacheManager.getCache(CacheConfig.PERMISSIONS_CACHE)).thenReturn(permissionsCache);
        lenient().when(cacheManager.getCache(CacheConfig.USER_ID_CACHE)).thenReturn(userIdCache);
        lenient().when(cacheManager.getCache(CacheConfig.WORKFLOW_RULES_CACHE)).thenReturn(workflowRulesCache);
        lenient().when(cacheManager.getCache(CacheConfig.GOVERNOR_LIMITS_CACHE)).thenReturn(governorLimitsCache);
        lenient().when(cacheManager.getCache(CacheConfig.LAYOUTS_CACHE)).thenReturn(layoutsCache);
        lenient().when(cacheManager.getCache(CacheConfig.BOOTSTRAP_CACHE)).thenReturn(bootstrapCache);
    }

    private String createEventMessage(String collectionName, String recordId, ChangeType changeType) throws Exception {
        RecordChangeEvent event = RecordChangeEvent.created("tenant-1", collectionName, recordId,
                Map.of("name", "test"), "user-1");
        event.setChangeType(changeType);
        return objectMapper.writeValueAsString(event);
    }

    @Nested
    @DisplayName("Collection Schema Cache Invalidation")
    class CollectionCacheTests {

        @Test
        @DisplayName("Should evict collection caches and bootstrap cache when collections record changes")
        void shouldEvictCollectionCachesOnCollectionChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("collections", "col-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(collectionsCache).clear();
            verify(collectionsListCache).clear();
            verify(bootstrapCache).clear();
        }

        @Test
        @DisplayName("Should evict collection caches and bootstrap cache when fields record changes")
        void shouldEvictCollectionCachesOnFieldChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("fields", "field-1", ChangeType.CREATED);

            listener.onRecordChanged(message);

            verify(collectionsCache).clear();
            verify(collectionsListCache).clear();
            verify(bootstrapCache).clear();
        }

        @Test
        @DisplayName("Should evict collection caches and bootstrap cache when record-types record changes")
        void shouldEvictCollectionCachesOnRecordTypeChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("record-types", "rt-1", ChangeType.DELETED);

            listener.onRecordChanged(message);

            verify(collectionsCache).clear();
            verify(collectionsListCache).clear();
            verify(bootstrapCache).clear();
        }
    }

    @Nested
    @DisplayName("Permission Cache Invalidation")
    class PermissionCacheTests {

        @Test
        @DisplayName("Should evict permissions cache when profiles record changes")
        void shouldEvictPermissionsCacheOnProfileChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("profiles", "profile-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(permissionsCache).clear();
        }

        @Test
        @DisplayName("Should evict permissions cache when permission-sets record changes")
        void shouldEvictPermissionsCacheOnPermissionSetChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("permission-sets", "ps-1", ChangeType.CREATED);

            listener.onRecordChanged(message);

            verify(permissionsCache).clear();
        }
    }

    @Nested
    @DisplayName("User Cache Invalidation")
    class UserCacheTests {

        @Test
        @DisplayName("Should evict user ID and permissions caches when users record changes")
        void shouldEvictUserCachesOnUserChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("users", "user-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(userIdCache).clear();
            verify(permissionsCache).clear();
        }
    }

    @Nested
    @DisplayName("Workflow Cache Invalidation")
    class WorkflowCacheTests {

        @Test
        @DisplayName("Should evict workflow rules cache when workflow-rules record changes")
        void shouldEvictWorkflowCacheOnWorkflowRuleChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("workflow-rules", "wr-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(workflowRulesCache).clear();
        }

        @Test
        @DisplayName("Should evict workflow rules cache when validation-rules record changes")
        void shouldEvictWorkflowCacheOnValidationRuleChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("validation-rules", "vr-1", ChangeType.CREATED);

            listener.onRecordChanged(message);

            verify(workflowRulesCache).clear();
        }
    }

    @Nested
    @DisplayName("Governor Limits Cache Invalidation")
    class GovernorLimitsCacheTests {

        @Test
        @DisplayName("Should evict governor limits cache and bootstrap cache when tenants record changes")
        void shouldEvictGovernorLimitsCacheOnTenantChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("tenants", "tenant-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(governorLimitsCache).clear();
            verify(bootstrapCache).clear();
        }
    }

    @Nested
    @DisplayName("Layouts Cache Invalidation")
    class LayoutsCacheTests {

        @Test
        @DisplayName("Should evict layouts cache when page-layouts record changes")
        void shouldEvictLayoutsCacheOnPageLayoutChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("page-layouts", "layout-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(layoutsCache).clear();
        }

        @Test
        @DisplayName("Should evict layouts cache when layout-assignments record changes")
        void shouldEvictLayoutsCacheOnLayoutAssignmentChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("layout-assignments", "la-1", ChangeType.CREATED);

            listener.onRecordChanged(message);

            verify(layoutsCache).clear();
        }
    }

    @Nested
    @DisplayName("Bootstrap Cache Invalidation")
    class BootstrapCacheTests {

        @Test
        @DisplayName("Should evict bootstrap cache when workers record changes")
        void shouldEvictBootstrapCacheOnWorkerChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("workers", "worker-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(bootstrapCache).clear();
        }

        @Test
        @DisplayName("Should evict bootstrap cache when collection-assignments record changes")
        void shouldEvictBootstrapCacheOnCollectionAssignmentChange() throws Exception {
            setupCacheMocks();
            String message = createEventMessage("collection-assignments", "ca-1", ChangeType.CREATED);

            listener.onRecordChanged(message);

            verify(bootstrapCache).clear();
        }
    }

    @Nested
    @DisplayName("Non-System Collection Events")
    class NonSystemCollectionTests {

        @Test
        @DisplayName("Should not evict any caches for user-defined collections")
        void shouldNotEvictCachesForUserDefinedCollections() throws Exception {
            String message = createEventMessage("products", "prod-1", ChangeType.CREATED);

            listener.onRecordChanged(message);

            verifyNoInteractions(cacheManager);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            assertDoesNotThrow(() -> listener.onRecordChanged("not valid json"));
        }

        @Test
        @DisplayName("Should handle null collection name gracefully")
        void shouldHandleNullCollectionNameGracefully() throws Exception {
            RecordChangeEvent event = new RecordChangeEvent();
            event.setCollectionName(null);
            String message = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> listener.onRecordChanged(message));
            verifyNoInteractions(cacheManager);
        }

        @Test
        @DisplayName("Should handle cache manager errors gracefully")
        void shouldHandleCacheManagerErrorsGracefully() throws Exception {
            when(cacheManager.getCache(CacheConfig.COLLECTIONS_CACHE)).thenThrow(new RuntimeException("Redis down"));
            String message = createEventMessage("collections", "col-1", ChangeType.UPDATED);

            // Should not propagate exception
            assertDoesNotThrow(() -> listener.onRecordChanged(message));
        }
    }
}
