package io.kelta.worker.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkerSystemCollectionCache Tests")
class WorkerSystemCollectionCacheTest {

    private WorkerSystemCollectionCache cache;
    private WorkerCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new WorkerCacheManager(new SimpleMeterRegistry());
        cache = new WorkerSystemCollectionCache(cacheManager);
    }

    @Test
    void getListResponse_returnsEmptyOnCacheMiss() {
        Optional<Map<String, Object>> result = cache.getListResponse("t1", "collections", "q1");
        assertThat(result).isEmpty();
    }

    @Test
    void putAndGetListResponse_returnsCachedValue() {
        Map<String, Object> response = Map.of("data", List.of(Map.of("id", "1")));

        cache.putListResponse("t1", "collections", "q1", response);

        Optional<Map<String, Object>> result = cache.getListResponse("t1", "collections", "q1");
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey("data");
    }

    @Test
    void getByIdResponse_returnsEmptyOnCacheMiss() {
        Optional<Map<String, Object>> result = cache.getByIdResponse("t1", "collections", "abc");
        assertThat(result).isEmpty();
    }

    @Test
    void putAndGetByIdResponse_returnsCachedValue() {
        Map<String, Object> response = Map.of("data", Map.of("id", "abc", "type", "collections"));

        cache.putByIdResponse("t1", "collections", "abc", response);

        Optional<Map<String, Object>> result = cache.getByIdResponse("t1", "collections", "abc");
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey("data");
    }

    @Test
    void evict_removesAllEntriesForCollection() {
        cache.putListResponse("t1", "collections", "q1", Map.of("data", List.of()));
        cache.putByIdResponse("t1", "collections", "abc", Map.of("data", Map.of()));
        cache.putListResponse("t1", "ui-pages", "q1", Map.of("data", List.of()));

        cache.evict("t1", "collections");

        assertThat(cache.getListResponse("t1", "collections", "q1")).isEmpty();
        assertThat(cache.getByIdResponse("t1", "collections", "abc")).isEmpty();
        // ui-pages should not be affected
        assertThat(cache.getListResponse("t1", "ui-pages", "q1")).isPresent();
    }

    @Test
    void evictAll_clearsEverything() {
        cache.putListResponse("t1", "collections", "q1", Map.of("data", List.of()));
        cache.putListResponse("t2", "ui-pages", "q1", Map.of("data", List.of()));

        cache.evictAll();

        assertThat(cache.getListResponse("t1", "collections", "q1")).isEmpty();
        assertThat(cache.getListResponse("t2", "ui-pages", "q1")).isEmpty();
    }

    @Test
    void differentTenants_haveSeparateCacheEntries() {
        cache.putListResponse("t1", "collections", "q1", Map.of("data", List.of(Map.of("id", "t1-data"))));
        cache.putListResponse("t2", "collections", "q1", Map.of("data", List.of(Map.of("id", "t2-data"))));

        assertThat(cache.getListResponse("t1", "collections", "q1")).isPresent();
        assertThat(cache.getListResponse("t2", "collections", "q1")).isPresent();

        // Evict only t1
        cache.evict("t1", "collections");

        assertThat(cache.getListResponse("t1", "collections", "q1")).isEmpty();
        assertThat(cache.getListResponse("t2", "collections", "q1")).isPresent();
    }

    @Test
    void nullTenantId_usesUnderscorePrefix() {
        cache.putListResponse(null, "collections", "q1", Map.of("data", List.of()));

        assertThat(cache.getListResponse(null, "collections", "q1")).isPresent();

        cache.evict(null, "collections");

        assertThat(cache.getListResponse(null, "collections", "q1")).isEmpty();
    }
}
