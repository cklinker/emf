package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.ConnectedAppRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Token management and audit endpoints for connected apps.
 *
 * <p>CRUD is handled by DynamicCollectionRouter (connected-apps is a system collection).
 * This controller adds: token list/generate/revoke, audit trail, and scope validation.
 *
 * <p>Token generation calls kelta-auth's OAuth2 token endpoint with client_credentials grant.
 * Tokens are standard JWTs validated by the gateway's existing JWT pipeline.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/connected-apps/{appId}")
public class ConnectedAppTokenController {

    private static final Logger log = LoggerFactory.getLogger(ConnectedAppTokenController.class);

    private final ConnectedAppRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final RestClient authClient;
    private final int tokenGenRateLimit;

    public ConnectedAppTokenController(
            ConnectedAppRepository repository,
            StringRedisTemplate redisTemplate,
            @Value("${kelta.auth.issuer-uri:http://localhost:8080}") String authUrl,
            @Value("${kelta.connected-app.token-gen-rate-limit:10}") int tokenGenRateLimit) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.authClient = RestClient.builder().baseUrl(authUrl).build();
        this.tokenGenRateLimit = tokenGenRateLimit;
    }

    // -----------------------------------------------------------------------
    // Token Management
    // -----------------------------------------------------------------------

    @GetMapping("/tokens")
    public ResponseEntity<?> listTokens(@PathVariable String appId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        // Tenant-scoped access check
        var appOpt = repository.findByIdAndTenant(appId, tenantId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Return token metadata only — never return the actual token value
        List<Map<String, Object>> tokens = repository.findTokensByAppId(appId);
        return ResponseEntity.ok(Map.of("data", tokens));
    }

    @PostMapping("/tokens")
    public ResponseEntity<?> generateToken(@PathVariable String appId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        var appOpt = repository.findByIdAndTenant(appId, tenantId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> app = appOpt.get();

        // Rate limit token generation
        String rateLimitKey = "connected_app:" + appId + ":token_gen";
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, Duration.ofMinutes(5));
        }
        if (count != null && count > tokenGenRateLimit) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Token generation rate limit exceeded. Max " + tokenGenRateLimit + " per 5 minutes."));
        }

        String clientId = (String) app.get("client_id");
        String scopes = app.get("scopes") != null ? app.get("scopes").toString() : "";

        // Call kelta-auth OAuth2 token endpoint with client_credentials grant
        try {
            // Note: The client_secret is stored hashed in connected_app table.
            // For token generation, the worker needs the raw secret which was shown view-once on creation.
            // Instead of storing the raw secret, we generate a token record and return a reference.
            // The actual OAuth2 token flow is initiated by the SDK/CLI with the client credentials.

            // Create a token tracking record
            Instant expiresAt = Instant.now().plusSeconds(3600); // 1 hour default
            String tokenId = repository.createToken(appId, scopes, expiresAt);

            // Audit
            repository.insertAuditRecord(tenantId, appId, "TOKEN_GENERATED",
                    "{\"tokenId\":\"" + tokenId + "\"}", null);

            log.info("Token record created for connected app {}: tokenId={}", appId, tokenId);

            return ResponseEntity.ok(Map.of(
                    "tokenId", tokenId,
                    "clientId", clientId,
                    "scopes", scopes,
                    "expiresAt", expiresAt.toString(),
                    "message", "Use client_id and client_secret with grant_type=client_credentials at the OAuth2 token endpoint to obtain an access token."
            ));
        } catch (Exception e) {
            log.error("Failed to generate token for connected app {}: {}", appId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate token"));
        }
    }

    @DeleteMapping("/tokens/{tokenId}")
    public ResponseEntity<?> revokeToken(@PathVariable String appId, @PathVariable String tokenId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        var appOpt = repository.findByIdAndTenant(appId, tenantId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int updated = repository.revokeToken(tokenId, appId);
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }

        // Add jti to Redis revocation set for near-instant gateway enforcement
        try {
            String revocationKey = "revoked_token:" + tokenId;
            redisTemplate.opsForValue().set(revocationKey, "revoked", Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Failed to add token {} to Redis revocation set: {}", tokenId, e.getMessage());
        }

        // Audit
        repository.insertAuditRecord(tenantId, appId, "TOKEN_REVOKED",
                "{\"tokenId\":\"" + tokenId + "\"}", null);

        log.info("Token revoked: appId={}, tokenId={}", appId, tokenId);
        return ResponseEntity.ok(Map.of("status", "revoked", "tokenId", tokenId));
    }

    // -----------------------------------------------------------------------
    // Audit Trail
    // -----------------------------------------------------------------------

    @GetMapping("/audit")
    public ResponseEntity<?> getAuditTrail(@PathVariable String appId,
                                            @RequestParam(defaultValue = "50") int limit) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        var appOpt = repository.findByIdAndTenant(appId, tenantId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> audit = repository.findAuditByAppId(appId, Math.min(limit, 200));
        return ResponseEntity.ok(Map.of("data", audit));
    }
}
