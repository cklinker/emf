package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Evaluates authorization using profile-based permissions from the control plane.
 * Maps HTTP methods to CRUD permissions and checks the user's effective permissions.
 */
@Component
public class ProfilePolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ProfilePolicyEvaluator.class);

    private final PermissionCache permissionCache;
    private final WebClient controlPlaneClient;

    public ProfilePolicyEvaluator(
            PermissionCache permissionCache,
            @Value("${emf.gateway.control-plane.url:http://localhost:8080}") String controlPlaneUrl) {
        this.permissionCache = permissionCache;
        this.controlPlaneClient = WebClient.builder()
                .baseUrl(controlPlaneUrl)
                .build();
    }

    /**
     * Check if the user has permission for the given HTTP method on a collection.
     * Fetches effective permissions from cache or control plane.
     */
    public Mono<Boolean> evaluate(GatewayPrincipal principal, String collectionId, HttpMethod method) {
        if (principal == null || collectionId == null) {
            return Mono.just(false);
        }

        String userId = getUserId(principal);
        if (userId == null) {
            return Mono.just(false);
        }

        return getEffectivePermissions(userId)
                .map(perms -> checkObjectPermission(perms, collectionId, method))
                .defaultIfEmpty(false);
    }

    /**
     * Check field visibility for the user.
     */
    public Mono<String> getFieldVisibility(GatewayPrincipal principal, String fieldId) {
        if (principal == null || fieldId == null) {
            return Mono.just("HIDDEN");
        }

        String userId = getUserId(principal);
        if (userId == null) {
            return Mono.just("HIDDEN");
        }

        return getEffectivePermissions(userId)
                .map(perms -> {
                    if (perms.getFieldPermissions() == null) {
                        return "VISIBLE"; // default if no field perms configured
                    }
                    return perms.getFieldPermissions().getOrDefault(fieldId, "VISIBLE");
                })
                .defaultIfEmpty("VISIBLE");
    }

    private Mono<EffectivePermissions> getEffectivePermissions(String userId) {
        return permissionCache.getPermissions(userId)
                .flatMap(opt -> opt.map(Mono::just)
                        .orElseGet(() -> fetchFromControlPlane(userId)));
    }

    private Mono<EffectivePermissions> fetchFromControlPlane(String userId) {
        return controlPlaneClient.get()
                .uri("/internal/permissions/{userId}", userId)
                .retrieve()
                .bodyToMono(EffectivePermissions.class)
                .flatMap(perms -> permissionCache.putPermissions(userId, perms)
                        .thenReturn(perms))
                .doOnError(err -> log.warn("Failed to fetch permissions for user {}: {}",
                        userId, err.getMessage()))
                .onErrorResume(err -> Mono.empty());
    }

    private boolean checkObjectPermission(EffectivePermissions perms, String collectionId, HttpMethod method) {
        if (perms.getObjectPermissions() == null) {
            return false;
        }

        EffectivePermissions.ObjectPermissions objPerm = perms.getObjectPermissions().get(collectionId);
        if (objPerm == null) {
            return false;
        }

        return switch (method.name()) {
            case "GET" -> objPerm.isCanRead();
            case "POST" -> objPerm.isCanCreate();
            case "PUT", "PATCH" -> objPerm.isCanEdit();
            case "DELETE" -> objPerm.isCanDelete();
            default -> false;
        };
    }

    private String getUserId(GatewayPrincipal principal) {
        // Try 'sub' claim first (standard OIDC), then fallback to 'user_id' claim
        Object sub = principal.getClaims().get("sub");
        if (sub instanceof String s && !s.isEmpty()) {
            return s;
        }
        Object userId = principal.getClaims().get("user_id");
        if (userId instanceof String s && !s.isEmpty()) {
            return s;
        }
        return principal.getUsername();
    }
}
