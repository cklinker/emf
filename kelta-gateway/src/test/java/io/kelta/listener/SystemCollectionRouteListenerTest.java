package io.kelta.gateway.listener;

import io.kelta.gateway.cache.GatewayCacheManager;
import io.kelta.gateway.route.RouteRegistry;
import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.RecordChangedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SystemCollectionRouteListener}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemCollectionRouteListener Tests")
class SystemCollectionRouteListenerTest {

    @Mock
    private RouteRegistry routeRegistry;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private GatewayCacheManager cacheManager;

    private ObjectMapper objectMapper;
    private SystemCollectionRouteListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new SystemCollectionRouteListener(
                routeRegistry, applicationEventPublisher, objectMapper, cacheManager
        );
    }

    private String createEventMessage(String collectionName, String recordId, ChangeType changeType) throws Exception {
        RecordChangedPayload payload = new RecordChangedPayload(
                collectionName, recordId, changeType,
                Map.of("name", "test"), null, java.util.List.of());
        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record." + changeType.name().toLowerCase(), "tenant-1", "user-1", payload);
        return objectMapper.writeValueAsString(event);
    }

    @Nested
    @DisplayName("Route Refresh Tests")
    class RouteRefreshTests {

        @Test
        @DisplayName("Should refresh routes when collections record changes")
        void shouldRefreshRoutesOnCollectionChange() throws Exception {
            String message = createEventMessage("collections", "col-1", ChangeType.CREATED);

            listener.onRecordChanged(message);

            ArgumentCaptor<RefreshRoutesEvent> eventCaptor = ArgumentCaptor.forClass(RefreshRoutesEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            assertNotNull(eventCaptor.getValue());
        }

        @Test
        @DisplayName("Should not refresh routes for non-collections system collections")
        void shouldNotRefreshRoutesForNonCollections() throws Exception {
            String message = createEventMessage("users", "user-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(applicationEventPublisher, never()).publishEvent(any(RefreshRoutesEvent.class));
        }

        @Test
        @DisplayName("Should not refresh routes for user-defined collections")
        void shouldNotRefreshRoutesForUserCollections() throws Exception {
            String message = createEventMessage("products", "prod-1", ChangeType.CREATED);

            listener.onRecordChanged(message);

            verify(applicationEventPublisher, never()).publishEvent(any(RefreshRoutesEvent.class));
        }
    }

    @Nested
    @DisplayName("Governor Limit Cache Refresh Tests")
    class GovernorLimitCacheTests {

        @Test
        @DisplayName("Should refresh governor limits when tenants collection changes")
        void shouldRefreshGovernorLimitsOnTenantChange() throws Exception {
            String message = createEventMessage("tenants", "tenant-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(cacheManager).refreshGovernorLimitsFromWorker();
        }

        @Test
        @DisplayName("Should NOT refresh governor limits for non-tenant collections")
        void shouldNotRefreshGovernorLimitsForNonTenantCollections() throws Exception {
            String message = createEventMessage("products", "prod-1", ChangeType.UPDATED);

            listener.onRecordChanged(message);

            verify(cacheManager, never()).refreshGovernorLimitsFromWorker();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            assertDoesNotThrow(() -> listener.onRecordChanged("not valid json"));
        }

        @Test
        @DisplayName("Should handle null collection name gracefully")
        void shouldHandleNullCollectionNameGracefully() throws Exception {
            RecordChangedPayload payload = new RecordChangedPayload();
            payload.setCollectionName(null);
            PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                    "record.created", "tenant-1", "user-1", payload);
            String message = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> listener.onRecordChanged(message));
            verifyNoInteractions(applicationEventPublisher);
        }
    }
}
