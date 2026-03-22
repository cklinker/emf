package io.kelta.gateway.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.gateway.filter.TenantResolutionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Resolves the user's identity (profileId, profileName) from the worker
 * and enriches the {@link GatewayPrincipal}.
 *
 * <p>Order: -50 (after JWT authentication at -100, before route authorization at 0).
 */
@Component
public class UserIdentityResolutionFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(UserIdentityResolutionFilter.class);
    private static final String CACHE_KEY_PREFIX = "user-identity:";

    private final WebClient webClient;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final PublicPathMatcher publicPathMatcher;
    private final Duration cacheTtl;

    public UserIdentityResolutionFilter(
            WebClient.Builder webClientBuilder,
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            PublicPathMatcher publicPathMatcher,
            @Value("${kelta.gateway.worker-service-url:http://kelta-worker:80}") String workerServiceUrl,
            @Value("${kelta.gateway.security.identity-cache-ttl-minutes:5}") int cacheTtlMinutes) {
        this.webClient = webClientBuilder.baseUrl(workerServiceUrl).build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.publicPathMatcher = publicPathMatcher;
        this.cacheTtl = Duration.ofMinutes(cacheTtlMinutes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (publicPathMatcher.isPublicRequest(exchange)) {
            return chain.filter(exchange);
        }

        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        if (principal == null) {
            return chain.filter(exchange);
        }

        String tenantId = TenantResolutionFilter.getTenantId(exchange);
        if (tenantId == null || tenantId.isBlank()) {
            return chain.filter(exchange);
        }

        // Set tenantId on principal from URL resolution (if not already set from JWT claims)
        if (principal.getTenantId() == null) {
            principal.setTenantId(tenantId);
        }

        // Skip worker lookup when profile is already in the JWT claims (kelta-auth tokens).
        // This eliminates the synchronous worker call per request for kelta-auth-issued tokens.
        if (principal.getProfileId() != null && !principal.getProfileId().isEmpty()) {
            log.debug("Profile already resolved from JWT claims for user: {}", principal.getUsername());
            return chain.filter(exchange);
        }

        String email = principal.getUsername();
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + email;

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(json -> {
                    parseIdentity(json, principal);
                    return Mono.just(json);
                })
                .switchIfEmpty(fetchFromWorker(tenantId, email, principal, cacheKey))
                .then(chain.filter(exchange));
    }

    private void parseIdentity(String json, GatewayPrincipal principal) {
        try {
            Map<String, String> identity = objectMapper.readValue(json,
                    new TypeReference<Map<String, String>>() {});
            principal.setProfileId(identity.get("profileId"));
            principal.setProfileName(identity.get("profileName"));
        } catch (Exception e) {
            log.warn("Failed to parse cached user identity: {}", e.getMessage());
        }
    }

    private Mono<String> fetchFromWorker(String tenantId, String email,
                                         GatewayPrincipal principal, String cacheKey) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/user-identity")
                        .queryParam("email", email)
                        .queryParam("tenantId", tenantId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(json -> {
                    parseIdentity(json, principal);
                    return redisTemplate.opsForValue().set(cacheKey, json, cacheTtl)
                            .thenReturn(json);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch user identity for {}/{}: {}",
                            tenantId, email, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
