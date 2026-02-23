package com.emf.gateway.listener;

import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
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
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

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
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private ObjectMapper objectMapper;
    private SystemCollectionRouteListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new SystemCollectionRouteListener(
                routeRegistry, applicationEventPublisher, redisTemplate, objectMapper
        );
    }

    private String createEventMessage(String collectionName, String recordId, ChangeType changeType) throws Exception {
        RecordChangeEvent event = RecordChangeEvent.created("tenant-1", collectionName, recordId,
                Map.of("name", "test"), "user-1");
        event.setChangeType(changeType);
        return objectMapper.writeValueAsString(event);
    }

    @Nested
    @DisplayName("Route Refresh Tests")
    class RouteRefreshTests {

        @Test
        @DisplayName("Should refresh routes when collections record changes")
        void shouldRefreshRoutesOnCollectionChange() throws Exception {
            // Arrange
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
            String message = createEventMessage("collections", "col-1", ChangeType.CREATED);

            // Act
            listener.onRecordChanged(message);

            // Assert
            ArgumentCaptor<RefreshRoutesEvent> eventCaptor = ArgumentCaptor.forClass(RefreshRoutesEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            assertNotNull(eventCaptor.getValue());
        }

        @Test
        @DisplayName("Should not refresh routes for non-collections system collections")
        void shouldNotRefreshRoutesForNonCollections() throws Exception {
            // Arrange
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
            String message = createEventMessage("users", "user-1", ChangeType.UPDATED);

            // Act
            listener.onRecordChanged(message);

            // Assert — no route refresh
            verify(applicationEventPublisher, never()).publishEvent(any(RefreshRoutesEvent.class));
        }

        @Test
        @DisplayName("Should not refresh routes for user-defined collections")
        void shouldNotRefreshRoutesForUserCollections() throws Exception {
            // Arrange
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
            String message = createEventMessage("products", "prod-1", ChangeType.CREATED);

            // Act
            listener.onRecordChanged(message);

            // Assert
            verify(applicationEventPublisher, never()).publishEvent(any(RefreshRoutesEvent.class));
        }
    }

    @Nested
    @DisplayName("Redis Cache Invalidation Tests")
    class RedisCacheTests {

        @Test
        @DisplayName("Should invalidate Redis cache for changed record")
        void shouldInvalidateRedisCacheForChangedRecord() throws Exception {
            // Arrange
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
            String message = createEventMessage("users", "user-123", ChangeType.UPDATED);

            // Act
            listener.onRecordChanged(message);

            // Assert
            verify(redisTemplate).delete("jsonapi:users:user-123");
        }

        @Test
        @DisplayName("Should invalidate Redis cache for collection change too")
        void shouldInvalidateRedisCacheForCollectionChange() throws Exception {
            // Arrange
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
            String message = createEventMessage("collections", "col-1", ChangeType.UPDATED);

            // Act
            listener.onRecordChanged(message);

            // Assert — both route refresh AND cache invalidation
            verify(redisTemplate).delete("jsonapi:collections:col-1");
            verify(applicationEventPublisher).publishEvent(any(RefreshRoutesEvent.class));
        }

        @Test
        @DisplayName("Should handle Redis errors gracefully")
        void shouldHandleRedisErrorsGracefully() throws Exception {
            // Arrange
            when(redisTemplate.delete(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));
            String message = createEventMessage("users", "user-1", ChangeType.UPDATED);

            // Act — should not throw
            assertDoesNotThrow(() -> listener.onRecordChanged(message));
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
            RecordChangeEvent event = new RecordChangeEvent();
            event.setCollectionName(null);
            String message = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> listener.onRecordChanged(message));
            verifyNoInteractions(applicationEventPublisher);
            verifyNoInteractions(redisTemplate);
        }
    }
}
