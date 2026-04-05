package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.BootstrapRepository;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Internal API endpoints for the gateway to bootstrap its configuration.
 *
 * <p>These endpoints replace the control plane's bootstrap/internal APIs.
 * They are called by the gateway on startup and periodically:
 * <ul>
 *   <li>{@code /internal/bootstrap} — collections + governor limits for route setup</li>
 *   <li>{@code /internal/tenants/slug-map} — tenant slug → ID mapping</li>
 *   <li>{@code /internal/oidc/by-issuer} — OIDC provider lookup for JWT validation</li>
 *   <li>{@code /internal/user-identity} — user identity resolution for Cerbos authorization</li>
 * </ul>
 *
 * <p>These endpoints are unauthenticated (internal network only, same as the
 * control plane's corresponding endpoints).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/internal")
public class InternalBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(InternalBootstrapController.class);

    private final BootstrapRepository repository;
    private final ObjectMapper objectMapper;

    public InternalBootstrapController(BootstrapRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the bootstrap configuration needed by the gateway on startup.
     *
     * <p>Includes all active collections (for route setup) and per-tenant
     * governor limits (for rate limiting).
     *
     * <p>This replaces the control plane's {@code GET /control/bootstrap} endpoint.
     *
     * @return bootstrap configuration with collections and governor limits
     */
    @GetMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> getBootstrapConfig() {
        log.debug("REST request to get gateway bootstrap configuration");

        List<Map<String, Object>> collections = loadCollections();
        Map<String, Map<String, Object>> governorLimits = loadGovernorLimits();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("collections", collections);
        response.put("governorLimits", governorLimits);

        log.info("Returning bootstrap config: {} collections, {} tenant governor limits",
                collections.size(), governorLimits.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns a slug → tenantId mapping for all routable tenants.
     *
     * <p>Includes ACTIVE and PROVISIONING tenants (excludes DECOMMISSIONED
     * and tenants without a slug). Used by the gateway's TenantSlugCache.
     *
     * <p>This replaces the control plane's {@code GET /control/tenants/slug-map} endpoint.
     *
     * @return map of tenant slug to tenant ID
     */
    @GetMapping("/tenants/slug-map")
    public ResponseEntity<Map<String, String>> getSlugMap() {
        log.debug("REST request to get tenant slug map");

        List<Map<String, Object>> rows = repository.findRoutableTenants();

        Map<String, String> slugMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("id");
            String slug = (String) row.get("slug");
            if (id != null && slug != null && !slug.isBlank()) {
                slugMap.put(slug, id);
            }
        }

        log.info("Returning tenant slug map with {} entries", slugMap.size());
        return ResponseEntity.ok(slugMap);
    }

    /**
     * Returns a lightweight governor limits map for periodic cache refresh.
     *
     * <p>Returns a simple map of tenantId to apiCallsPerDay. Used by the
     * gateway's {@code GatewayCacheManager} to periodically refresh the
     * governor limit cache without fetching the full bootstrap payload.
     *
     * @return map of tenantId to apiCallsPerDay
     */
    @GetMapping("/governor-limits")
    public ResponseEntity<Map<String, Integer>> getGovernorLimitsMap() {
        log.debug("REST request to get governor limits map");

        Map<String, Map<String, Object>> fullLimits = loadGovernorLimits();

        Map<String, Integer> limitsMap = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : fullLimits.entrySet()) {
            Object apiCalls = entry.getValue().get("apiCallsPerDay");
            if (apiCalls instanceof Number num) {
                limitsMap.put(entry.getKey(), num.intValue());
            }
        }

        log.info("Returning governor limits map with {} entries", limitsMap.size());
        return ResponseEntity.ok(limitsMap);
    }

    /**
     * Looks up an OIDC provider by its issuer URI.
     *
     * <p>Returns the provider's JWKS URI and audience for JWT validation.
     * The gateway uses this to resolve the correct JWKS endpoint for each
     * JWT issuer it encounters.
     *
     * <p>This replaces the control plane's {@code GET /internal/oidc/by-issuer} endpoint.
     *
     * @param issuer the OIDC issuer URI to look up
     * @return OIDC provider info including jwksUri and audience
     */
    @GetMapping("/oidc/by-issuer")
    public ResponseEntity<Map<String, Object>> getOidcProviderByIssuer(
            @RequestParam String issuer,
            @RequestParam(required = false) String tenantId) {

        Optional<Map<String, Object>> providerOpt;

        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("Internal lookup: OIDC provider by issuer={} tenant={}", issuer, tenantId);
            providerOpt = repository.findOidcProviderByIssuerAndTenant(issuer, tenantId);
        } else {
            log.warn("Internal lookup: OIDC provider by issuer={} WITHOUT tenant scope "
                    + "(cross-tenant risk — gateway should always provide tenantId)", issuer);
            providerOpt = repository.findOidcProviderByIssuer(issuer);
        }

        if (providerOpt.isEmpty()) {
            log.warn("No active OIDC provider found for issuer={} tenant={}", issuer, tenantId);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> provider = providerOpt.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", provider.get("id"));
        response.put("name", provider.get("name"));
        response.put("issuer", provider.get("issuer"));
        response.put("jwksUri", provider.get("jwks_uri"));
        response.put("audience", provider.get("audience"));
        response.put("clientId", provider.get("client_id"));
        response.put("clientSecretEnc", provider.get("client_secret_enc"));
        response.put("rolesClaim", provider.get("roles_claim"));
        response.put("rolesMapping", provider.get("roles_mapping"));
        response.put("groupsClaim", provider.get("groups_claim"));
        response.put("groupsProfileMapping", provider.get("groups_profile_mapping"));
        response.put("authorizationUri", provider.get("authorization_uri"));
        response.put("tokenUri", provider.get("token_uri"));
        response.put("userinfoUri", provider.get("userinfo_uri"));
        response.put("endSessionUri", provider.get("end_session_uri"));
        response.put("discoveryStatus", provider.get("discovery_status"));

        return ResponseEntity.ok(response);
    }

    /**
     * Returns lightweight user identity for gateway Cerbos authorization.
     *
     * <p>Returns userId, profileId, and profileName. The gateway caches this
     * in Redis and uses profileId to build the Cerbos principal.
     *
     * @param email    the user's email address
     * @param tenantId the tenant UUID
     * @return user identity with profileId and profileName
     */
    @GetMapping("/user-identity")
    public ResponseEntity<Map<String, Object>> getUserIdentity(
            @RequestParam String email,
            @RequestParam String tenantId) {
        log.debug("Internal user-identity lookup for email={} tenant={}", email, tenantId);

        Optional<Map<String, Object>> identityOpt = repository.findUserIdentity(email, tenantId);

        if (identityOpt.isEmpty()) {
            log.warn("No active user found for email={} tenant={}", email, tenantId);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> row = identityOpt.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", row.get("id"));
        response.put("profileId", row.get("profile_id"));
        response.put("profileName", row.get("profile_name"));

        return ResponseEntity.ok(response);
    }

    /**
     * Lists all active OIDC providers for a tenant.
     *
     * <p>Used by kelta-auth to build the federated login page and
     * dynamic client registrations for each tenant.
     *
     * @param tenantId the tenant UUID
     * @return list of active OIDC provider configurations
     */
    @GetMapping("/oidc/providers")
    public ResponseEntity<List<Map<String, Object>>> getOidcProvidersByTenant(
            @RequestParam String tenantId) {
        log.debug("Internal lookup: OIDC providers for tenant={}", tenantId);

        List<Map<String, Object>> providers = repository.findActiveOidcProvidersByTenant(tenantId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> provider : providers) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", provider.get("id"));
            response.put("name", provider.get("name"));
            response.put("issuer", provider.get("issuer"));
            response.put("jwksUri", provider.get("jwks_uri"));
            response.put("audience", provider.get("audience"));
            response.put("clientId", provider.get("client_id"));
            response.put("clientSecretEnc", provider.get("client_secret_enc"));
            response.put("rolesClaim", provider.get("roles_claim"));
            response.put("rolesMapping", provider.get("roles_mapping"));
            response.put("groupsClaim", provider.get("groups_claim"));
            response.put("groupsProfileMapping", provider.get("groups_profile_mapping"));
            response.put("authorizationUri", provider.get("authorization_uri"));
            response.put("tokenUri", provider.get("token_uri"));
            response.put("userinfoUri", provider.get("userinfo_uri"));
            response.put("endSessionUri", provider.get("end_session_uri"));
            response.put("discoveryStatus", provider.get("discovery_status"));
            response.put("emailClaim", provider.get("email_claim"));
            response.put("usernameClaim", provider.get("username_claim"));
            response.put("nameClaim", provider.get("name_claim"));
            result.add(response);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Creates or updates a user during JIT (Just-In-Time) provisioning from SSO login.
     *
     * <p>If the user already exists:
     * <ul>
     *   <li>Updates last_login_at and login_count</li>
     *   <li>Returns the existing user identity</li>
     * </ul>
     *
     * <p>If the user does not exist:
     * <ul>
     *   <li>Creates with the provided profile (if matched from groups)</li>
     *   <li>Creates as PENDING_ACTIVATION if no profile matched</li>
     * </ul>
     *
     * @param body contains email, tenantId, firstName, lastName, profileId (nullable)
     * @return user identity with userId, profileId, profileName, status
     */
    @PostMapping("/user-identity/jit")
    public ResponseEntity<Map<String, Object>> jitProvisionUser(
            @RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String tenantId = (String) body.get("tenantId");
        String firstName = (String) body.get("firstName");
        String lastName = (String) body.get("lastName");
        String profileId = (String) body.get("profileId");

        log.info("JIT provision: email={} tenant={} profileId={}", email, tenantId, profileId);

        // Set tenant context so RLS policies filter correctly
        TenantContext.set(tenantId);
        try {
            return doJitProvisionUser(email, tenantId, firstName, lastName, profileId);
        } finally {
            TenantContext.clear();
        }
    }

    private ResponseEntity<Map<String, Object>> doJitProvisionUser(
            String email, String tenantId, String firstName, String lastName, String profileId) {
        // Check if user already exists (any status) — try email first, then username
        // The OIDC email claim may contain the username (e.g. "admin") rather than
        // the full email ("admin@kelta.local"), so a username fallback is needed.
        Optional<Map<String, Object>> existingOpt = repository.findUserByEmailAnyStatus(email, tenantId);
        if (existingOpt.isEmpty()) {
            existingOpt = repository.findUserByUsernameAnyStatus(email, tenantId);
        }

        if (existingOpt.isPresent()) {
            Map<String, Object> existing = existingOpt.get();
            String status = (String) existing.get("status");

            // Update last login
            repository.getJdbcTemplate().update(
                    "UPDATE platform_user SET last_login_at = CURRENT_TIMESTAMP, login_count = login_count + 1 WHERE id = ?",
                    existing.get("id"));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("userId", existing.get("id"));
            response.put("profileId", existing.get("profile_id"));
            response.put("status", status);
            response.put("created", false);

            // Re-resolve profile name
            Optional<Map<String, Object>> identityOpt = repository.findUserIdentity(email, tenantId);
            if (identityOpt.isPresent()) {
                response.put("profileName", identityOpt.get().get("profile_name"));
            }

            return ResponseEntity.ok(response);
        }

        // Create new user via JIT provisioning
        String userId = UUID.randomUUID().toString();
        String status = profileId != null ? "ACTIVE" : "PENDING_ACTIVATION";

        repository.getJdbcTemplate().update("""
                INSERT INTO platform_user (id, tenant_id, email, username, first_name, last_name,
                    profile_id, status, last_login_at, login_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId, tenantId, email, email, firstName, lastName, profileId, status);

        log.info("JIT provisioned user: id={} email={} status={}", userId, email, status);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("profileId", profileId);
        response.put("status", status);
        response.put("created", true);

        // Resolve profile name if profile was assigned
        if (profileId != null) {
            Optional<Map<String, Object>> identityOpt = repository.findUserIdentity(email, tenantId);
            if (identityOpt.isPresent()) {
                response.put("profileName", identityOpt.get().get("profile_name"));
            }
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private List<Map<String, Object>> loadCollections() {
        List<Map<String, Object>> rows = repository.findActiveCollections();

        List<Map<String, Object>> collections = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("name");

            // Skip the virtual __control-plane collection
            if ("__control-plane".equals(name)) {
                continue;
            }

            Map<String, Object> collection = new LinkedHashMap<>();
            collection.put("id", row.get("id"));
            collection.put("name", name);
            collection.put("path", row.get("path"));
            collection.put("systemCollection", Boolean.TRUE.equals(row.get("system_collection")));
            collections.add(collection);
        }

        return collections;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadGovernorLimits() {
        List<Map<String, Object>> rows = repository.findTenantLimits();

        Map<String, Map<String, Object>> governorLimits = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String tenantId = (String) row.get("id");
            Object limitsObj = row.get("limits");

            int apiCallsPerDay = 100_000; // default
            if (limitsObj instanceof String limitsStr && !limitsStr.isBlank()) {
                try {
                    Map<String, Object> limits = objectMapper.readValue(limitsStr,
                            objectMapper.getTypeFactory().constructMapType(
                                    HashMap.class, String.class, Object.class));
                    Object apiCalls = limits.get("apiCallsPerDay");
                    if (apiCalls instanceof Number num) {
                        apiCallsPerDay = num.intValue();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse limits for tenant {}: {}", tenantId, e.getMessage());
                }
            } else if (limitsObj instanceof Map) {
                Map<String, Object> limits = (Map<String, Object>) limitsObj;
                Object apiCalls = limits.get("apiCallsPerDay");
                if (apiCalls instanceof Number num) {
                    apiCallsPerDay = num.intValue();
                }
            } else if (limitsObj != null) {
                // Handle PGobject and other types by converting to string first
                String limitsStr = limitsObj.toString();
                if (!limitsStr.isBlank()) {
                    try {
                        Map<String, Object> limits = objectMapper.readValue(limitsStr,
                                objectMapper.getTypeFactory().constructMapType(
                                        HashMap.class, String.class, Object.class));
                        Object apiCalls = limits.get("apiCallsPerDay");
                        if (apiCalls instanceof Number num) {
                            apiCallsPerDay = num.intValue();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse limits from {} for tenant {}: {}",
                                limitsObj.getClass().getSimpleName(), tenantId, e.getMessage());
                    }
                }
            }

            Map<String, Object> limitConfig = new LinkedHashMap<>();
            limitConfig.put("apiCallsPerDay", apiCallsPerDay);
            governorLimits.put(tenantId, limitConfig);
        }

        return governorLimits;
    }
}
