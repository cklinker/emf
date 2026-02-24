package com.emf.gateway.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PermissionResolutionService}.
 */
@ExtendWith(MockitoExtension.class)
class PermissionResolutionServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private ObjectMapper objectMapper;
    private MockWebServer mockWebServer;
    private PermissionResolutionService service;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String workerUrl = mockWebServer.url("/").toString();
        // Remove trailing slash
        if (workerUrl.endsWith("/")) {
            workerUrl = workerUrl.substring(0, workerUrl.length() - 1);
        }

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service = new PermissionResolutionService(
                redisTemplate,
                WebClient.builder(),
                objectMapper,
                workerUrl,
                5
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Should return cached permissions on Redis hit")
    void shouldReturnCachedPermissions() {
        // Given: cached permission data in Redis
        String cachedJson = """
                {
                  "userId": "user-123",
                  "systemPermissions": {"API_ACCESS": true},
                  "objectPermissions": {
                    "coll-1": {"canCreate": true, "canRead": true, "canEdit": false,
                               "canDelete": false, "canViewAll": false, "canModifyAll": false}
                  },
                  "fieldPermissions": {}
                }
                """;
        when(valueOps.get("permissions:tenant-1:user@test.com"))
                .thenReturn(Mono.just(cachedJson));

        // When
        StepVerifier.create(service.resolvePermissions("tenant-1", "user@test.com"))
                .assertNext(perms -> {
                    assertThat(perms.userId()).isEqualTo("user-123");
                    assertThat(perms.hasSystemPermission("API_ACCESS")).isTrue();
                    assertThat(perms.getObjectPermissions("coll-1").canCreate()).isTrue();
                    assertThat(perms.getObjectPermissions("coll-1").canRead()).isTrue();
                    assertThat(perms.getObjectPermissions("coll-1").canEdit()).isFalse();
                })
                .expectComplete()
                .verify();

        // No worker call should have been made
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should fetch from worker on cache miss and cache result")
    void shouldFetchFromWorkerOnCacheMiss() throws Exception {
        // Given: no cached data
        when(valueOps.get("permissions:tenant-1:user@test.com"))
                .thenReturn(Mono.empty());
        when(valueOps.set(eq("permissions:tenant-1:user@test.com"), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        // Worker returns permission data
        String workerResponse = """
                {
                  "userId": "user-456",
                  "systemPermissions": {"API_ACCESS": true, "MANAGE_USERS": false},
                  "objectPermissions": {
                    "coll-1": {"canCreate": true, "canRead": true, "canEdit": true,
                               "canDelete": false, "canViewAll": false, "canModifyAll": false}
                  },
                  "fieldPermissions": {
                    "coll-1": {"field-1": "VISIBLE", "field-2": "READ_ONLY"}
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(workerResponse));

        // When
        StepVerifier.create(service.resolvePermissions("tenant-1", "user@test.com"))
                .assertNext(perms -> {
                    assertThat(perms.userId()).isEqualTo("user-456");
                    assertThat(perms.hasSystemPermission("API_ACCESS")).isTrue();
                    assertThat(perms.hasSystemPermission("MANAGE_USERS")).isFalse();
                    assertThat(perms.getObjectPermissions("coll-1").canCreate()).isTrue();
                    assertThat(perms.getObjectPermissions("coll-1").canDelete()).isFalse();
                    assertThat(perms.fieldPermissions().get("coll-1").get("field-1")).isEqualTo("VISIBLE");
                })
                .expectComplete()
                .verify();

        // Verify worker was called with correct params
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("/internal/permissions");
        assertThat(request.getPath()).contains("email=user");
        assertThat(request.getPath()).contains("tenantId=tenant-1");

        // Verify cached
        verify(valueOps).set(eq("permissions:tenant-1:user@test.com"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Should return allPermissive on worker error")
    void shouldReturnAllPermissiveOnWorkerError() {
        // Given: no cached data
        when(valueOps.get("permissions:tenant-1:user@test.com"))
                .thenReturn(Mono.empty());

        // Worker returns error
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // When
        StepVerifier.create(service.resolvePermissions("tenant-1", "user@test.com"))
                .assertNext(perms -> {
                    assertThat(perms.isAllPermissive()).isTrue();
                })
                .expectComplete()
                .verify();
    }

    @Test
    @DisplayName("Should return allPermissive on Redis error")
    void shouldReturnAllPermissiveOnRedisError() {
        // Given: Redis throws
        when(valueOps.get("permissions:tenant-1:user@test.com"))
                .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

        // When
        StepVerifier.create(service.resolvePermissions("tenant-1", "user@test.com"))
                .assertNext(perms -> {
                    assertThat(perms.isAllPermissive()).isTrue();
                })
                .expectComplete()
                .verify();
    }

    @Test
    @DisplayName("Should still return permissions when cache write fails")
    void shouldReturnPermissionsWhenCacheWriteFails() {
        // Given: no cached data
        when(valueOps.get("permissions:tenant-1:user@test.com"))
                .thenReturn(Mono.empty());
        when(valueOps.set(eq("permissions:tenant-1:user@test.com"), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis write failed")));

        // Worker returns data
        String workerResponse = """
                {
                  "userId": "user-789",
                  "systemPermissions": {"API_ACCESS": true},
                  "objectPermissions": {},
                  "fieldPermissions": {}
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(workerResponse));

        // When
        StepVerifier.create(service.resolvePermissions("tenant-1", "user@test.com"))
                .assertNext(perms -> {
                    assertThat(perms.userId()).isEqualTo("user-789");
                    assertThat(perms.hasSystemPermission("API_ACCESS")).isTrue();
                })
                .expectComplete()
                .verify();
    }

    @Test
    @DisplayName("Should handle empty object permissions from worker")
    void shouldHandleEmptyObjectPermissions() {
        when(valueOps.get("permissions:tenant-1:user@test.com"))
                .thenReturn(Mono.empty());
        when(valueOps.set(eq("permissions:tenant-1:user@test.com"), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        String workerResponse = """
                {
                  "userId": "user-101",
                  "systemPermissions": {},
                  "objectPermissions": {},
                  "fieldPermissions": {}
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(workerResponse));

        StepVerifier.create(service.resolvePermissions("tenant-1", "user@test.com"))
                .assertNext(perms -> {
                    assertThat(perms.userId()).isEqualTo("user-101");
                    assertThat(perms.objectPermissions()).isEmpty();
                    assertThat(perms.getObjectPermissions("any-collection"))
                            .isEqualTo(ObjectPermissions.NONE);
                })
                .expectComplete()
                .verify();
    }

    @Nested
    @DisplayName("Cache Eviction Tests")
    class EvictionTests {

        @Test
        @DisplayName("Should evict all permission cache entries for a tenant")
        void shouldEvictPermissionCacheForTenant() {
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(Flux.just(
                            "permissions:tenant-1:user1@test.com",
                            "permissions:tenant-1:user2@test.com"
                    ));
            when(redisTemplate.delete(any(String[].class)))
                    .thenReturn(Mono.just(2L));

            StepVerifier.create(service.evictPermissionCache("tenant-1"))
                    .expectComplete()
                    .verify();

            verify(redisTemplate).delete(any(String[].class));
        }

        @Test
        @DisplayName("Should handle empty scan results")
        void shouldHandleEmptyScanResults() {
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(Flux.empty());

            StepVerifier.create(service.evictPermissionCache("tenant-1"))
                    .expectComplete()
                    .verify();

            verify(redisTemplate, never()).delete(any(String[].class));
        }

        @Test
        @DisplayName("Should handle null tenant ID")
        void shouldHandleNullTenantId() {
            StepVerifier.create(service.evictPermissionCache(null))
                    .expectComplete()
                    .verify();

            verify(redisTemplate, never()).scan(any(ScanOptions.class));
        }

        @Test
        @DisplayName("Should handle Redis error during eviction")
        void shouldHandleRedisErrorDuringEviction() {
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(Flux.error(new RuntimeException("Redis unavailable")));

            StepVerifier.create(service.evictPermissionCache("tenant-1"))
                    .expectComplete()
                    .verify();
        }
    }
}
