package com.emf.gateway.authz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves effective permissions for a user by checking Redis cache first,
 * then falling back to the worker's {@code /internal/permissions} endpoint.
 *
 * <p>Cache key pattern: {@code permissions:{tenantId}:{email}}
 *
 * <p>Fail-open: if Redis or the worker is unreachable, returns all-permissive
 * permissions to prevent a permission system failure from blocking all requests.
 */
@Service
public class PermissionResolutionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionResolutionService.class);

    static final String CACHE_KEY_PREFIX = "permissions:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public PermissionResolutionService(
            ReactiveRedisTemplate<String, String> redisTemplate,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl,
            @Value("${emf.gateway.security.permissions-cache-ttl-minutes:5}") int cacheTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.webClient = webClientBuilder.baseUrl(workerServiceUrl).build();
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofMinutes(cacheTtlMinutes);
    }

    /**
     * Resolves permissions for a user. Checks Redis cache first, then falls
     * back to the worker's /internal/permissions endpoint.
     *
     * @param tenantId the tenant UUID
     * @param email    the user's email address
     * @return resolved permissions, never null
     */
    public Mono<ResolvedPermissions> resolvePermissions(String tenantId, String email) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + email;

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserialize)
                .switchIfEmpty(
                        fetchFromWorker(tenantId, email)
                                .flatMap(perms -> cacheAndReturn(cacheKey, perms))
                )
                .onErrorResume(e -> {
                    log.warn("Error resolving permissions for {}/{}, allowing request: {}",
                            tenantId, email, e.getMessage());
                    return Mono.just(ResolvedPermissions.allPermissive());
                });
    }

    /**
     * Evicts all permission cache entries for a tenant.
     * Uses Redis SCAN to find and delete matching keys.
     *
     * @param tenantId the tenant UUID
     * @return completion signal
     */
    public Mono<Void> evictPermissionCache(String tenantId) {
        if (tenantId == null) {
            return Mono.empty();
        }
        String pattern = CACHE_KEY_PREFIX + tenantId + ":*";
        return redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(100).build())
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.empty();
                    }
                    log.info("Evicting {} permission cache entries for tenant {}", keys.size(), tenantId);
                    return redisTemplate.delete(keys.toArray(new String[0])).then();
                })
                .onErrorResume(e -> {
                    log.warn("Failed to evict permission cache for tenant {}: {}",
                            tenantId, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<ResolvedPermissions> fetchFromWorker(String tenantId, String email) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/permissions")
                        .queryParam("email", email)
                        .queryParam("tenantId", tenantId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(this::parseWorkerResponse)
                .doOnNext(p -> log.debug("Fetched permissions from worker for {}/{}: {} sys, {} obj perms",
                        tenantId, email, p.systemPermissions().size(), p.objectPermissions().size()))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch permissions from worker for {}/{}: {}",
                            tenantId, email, e.getMessage());
                    return Mono.just(ResolvedPermissions.allPermissive());
                });
    }

    @SuppressWarnings("unchecked")
    private Mono<ResolvedPermissions> parseWorkerResponse(String json) {
        try {
            Map<String, Object> raw = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String userId = (String) raw.get("userId");

            Map<String, Boolean> systemPerms = raw.get("systemPermissions") != null
                    ? (Map<String, Boolean>) raw.get("systemPermissions")
                    : Collections.emptyMap();

            Map<String, ObjectPermissions> objectPerms = new HashMap<>();
            Map<String, Object> rawObjectPerms = raw.get("objectPermissions") != null
                    ? (Map<String, Object>) raw.get("objectPermissions")
                    : Collections.emptyMap();
            for (Map.Entry<String, Object> entry : rawObjectPerms.entrySet()) {
                Map<String, Object> op = (Map<String, Object>) entry.getValue();
                objectPerms.put(entry.getKey(), new ObjectPermissions(
                        toBool(op.get("canCreate")),
                        toBool(op.get("canRead")),
                        toBool(op.get("canEdit")),
                        toBool(op.get("canDelete")),
                        toBool(op.get("canViewAll")),
                        toBool(op.get("canModifyAll"))
                ));
            }

            Map<String, Map<String, String>> fieldPerms = raw.get("fieldPermissions") != null
                    ? (Map<String, Map<String, String>>) raw.get("fieldPermissions")
                    : Collections.emptyMap();

            return Mono.just(new ResolvedPermissions(userId, systemPerms, objectPerms, fieldPerms));
        } catch (Exception e) {
            log.error("Failed to parse worker permission response: {}", e.getMessage());
            return Mono.just(ResolvedPermissions.allPermissive());
        }
    }

    private Mono<ResolvedPermissions> cacheAndReturn(String cacheKey, ResolvedPermissions perms) {
        try {
            String json = objectMapper.writeValueAsString(serializeForCache(perms));
            return redisTemplate.opsForValue().set(cacheKey, json, cacheTtl)
                    .thenReturn(perms)
                    .onErrorResume(e -> {
                        log.warn("Failed to cache permissions at {}: {}", cacheKey, e.getMessage());
                        return Mono.just(perms);
                    });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize permissions for caching: {}", e.getMessage());
            return Mono.just(perms);
        }
    }

    private Map<String, Object> serializeForCache(ResolvedPermissions perms) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", perms.userId());
        map.put("systemPermissions", perms.systemPermissions());

        Map<String, Map<String, Object>> objectPerms = new HashMap<>();
        for (Map.Entry<String, ObjectPermissions> entry : perms.objectPermissions().entrySet()) {
            ObjectPermissions op = entry.getValue();
            Map<String, Object> opMap = new HashMap<>();
            opMap.put("canCreate", op.canCreate());
            opMap.put("canRead", op.canRead());
            opMap.put("canEdit", op.canEdit());
            opMap.put("canDelete", op.canDelete());
            opMap.put("canViewAll", op.canViewAll());
            opMap.put("canModifyAll", op.canModifyAll());
            objectPerms.put(entry.getKey(), opMap);
        }
        map.put("objectPermissions", objectPerms);
        map.put("fieldPermissions", perms.fieldPermissions());
        return map;
    }

    private Mono<ResolvedPermissions> deserialize(String json) {
        return parseWorkerResponse(json);
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return false;
    }
}
