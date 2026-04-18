package io.kelta.auth.federation;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import io.kelta.auth.model.KeltaUserDetails;
import io.kelta.auth.service.WorkerClient;
import io.kelta.auth.service.WorkerClient.OidcProviderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import io.kelta.crypto.EncryptionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Maps a federated OIDC user to an internal {@link KeltaUserDetails} via JIT provisioning.
 *
 * <p>After a user authenticates with an external IdP, this service:
 * <ol>
 *   <li>Extracts identity claims (email, name) from the external token</li>
 *   <li>Resolves profile from OIDC groups using the provider's claim mapping</li>
 *   <li>Creates or updates the internal {@code platform_user} record</li>
 *   <li>Returns a {@link KeltaUserDetails} for token minting</li>
 * </ol>
 *
 * <p>Profile resolution order: (1) the provider's {@code groups_profile_mapping}
 * — for any group present on the external token, look up the mapped profile by
 * name in the tenant's {@code profile} table; (2) fall back to the seeded
 * {@value #FALLBACK_PROFILE_NAME} profile so every federated user can log in
 * with a login-only role even if none of their groups are mapped. If the
 * fallback profile itself is missing (unprovisioned tenant) the user is created
 * as {@code PENDING_ACTIVATION} and an admin must assign a profile manually.
 */
@Service
@ConditionalOnBean(EncryptionService.class)
public class FederatedUserMapper {

    private static final Logger log = LoggerFactory.getLogger(FederatedUserMapper.class);

    /**
     * Seeded login-only profile from {@code TenantProvisioningHook}. Grants no
     * data access on its own — admins must layer Permission Sets on top — so
     * assigning it to every unmapped federated user is safe by default.
     */
    static final String FALLBACK_PROFILE_NAME = "Minimum Access";

    private final WorkerClient workerClient;
    private final ObjectMapper objectMapper;

    public FederatedUserMapper(WorkerClient workerClient, ObjectMapper objectMapper) {
        this.workerClient = workerClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Maps an external OIDC user to an internal KeltaUserDetails.
     *
     * @param oidcUser the authenticated external OIDC user
     * @param tenantId the tenant this user belongs to
     * @param provider the OIDC provider configuration (for claim mappings)
     * @return KeltaUserDetails for token minting, or empty if provisioning failed
     */
    public Optional<KeltaUserDetails> mapUser(OidcUser oidcUser, String tenantId, OidcProviderInfo provider) {
        // Extract identity from external claims using provider's claim mapping
        String email = extractClaim(oidcUser, provider.emailClaim(), "email");
        if (email == null) {
            log.warn("No email found in external OIDC token for provider {}", provider.id());
            return Optional.empty();
        }

        String displayName = extractClaim(oidcUser, provider.nameClaim(), "name");
        String firstName = extractFirstName(oidcUser, displayName);
        String lastName = extractLastName(oidcUser, displayName);

        // Resolve profile from groups, falling back to the "Minimum Access"
        // seed profile so unmapped federated users can still log in.
        List<String> groups = extractGroups(oidcUser, provider.groupsClaim());
        String profileId = resolveProfileFromGroups(groups, provider.groupsProfileMapping(), tenantId);
        if (profileId == null) {
            profileId = workerClient.findProfileByName(FALLBACK_PROFILE_NAME, tenantId)
                    .map(WorkerClient.ProfileInfo::id)
                    .orElse(null);
            if (profileId != null) {
                log.info("No group-based profile match for email={} — assigning {} fallback",
                        email, FALLBACK_PROFILE_NAME);
            }
        }

        log.info("Federated user mapping: email={} groups={} profileId={} tenant={}",
                email, groups, profileId, tenantId);

        // JIT provision the user
        Optional<WorkerClient.JitProvisionResult> result =
                workerClient.jitProvisionUser(email, tenantId, firstName, lastName, profileId);

        if (result.isEmpty()) {
            log.error("JIT provisioning failed for email={} tenant={}", email, tenantId);
            return Optional.empty();
        }

        WorkerClient.JitProvisionResult provision = result.get();

        if ("PENDING_ACTIVATION".equals(provision.status())) {
            log.info("User {} created as PENDING_ACTIVATION (no profile match)", email);
            return Optional.empty(); // User cannot log in yet
        }

        if (!"ACTIVE".equals(provision.status())) {
            log.warn("User {} has status {} — cannot login", email, provision.status());
            return Optional.empty();
        }

        return Optional.of(new KeltaUserDetails(
                provision.userId(),
                email,
                tenantId,
                provision.profileId(),
                provision.profileName(),
                displayName != null ? displayName : email,
                "", // No password hash for SSO users
                true, // active
                false, // not locked
                false  // forceChangePassword is never true for SSO
        ));
    }

    private String extractClaim(OidcUser oidcUser, String configuredClaim, String defaultClaim) {
        String claimName = configuredClaim != null && !configuredClaim.isBlank()
                ? configuredClaim : defaultClaim;

        Object value = oidcUser.getClaim(claimName);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }

        // Fallback: try the default claim if configured claim didn't work
        if (!claimName.equals(defaultClaim)) {
            value = oidcUser.getClaim(defaultClaim);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractGroups(OidcUser oidcUser, String groupsClaim) {
        String claimName = groupsClaim != null && !groupsClaim.isBlank() ? groupsClaim : "groups";

        Object value = oidcUser.getClaim(claimName);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        if (value instanceof String str && !str.isBlank()) {
            return Arrays.asList(str.split(","));
        }
        return List.of();
    }

    private String resolveProfileFromGroups(List<String> groups,
                                             String groupsProfileMappingJson,
                                             String tenantId) {
        if (groups.isEmpty() || groupsProfileMappingJson == null || groupsProfileMappingJson.isBlank()) {
            return null;
        }

        try {
            Map<String, String> mapping = objectMapper.readValue(
                    groupsProfileMappingJson, new TypeReference<>() {});

            // Priority: System Administrator first
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                if (groups.contains(entry.getKey()) && "System Administrator".equals(entry.getValue())) {
                    String id = lookupProfileId(entry.getValue(), tenantId);
                    if (id != null) return id;
                }
            }

            // First matching group
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                if (groups.contains(entry.getKey())) {
                    String id = lookupProfileId(entry.getValue(), tenantId);
                    if (id != null) return id;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse groups-profile mapping: {}", e.getMessage());
        }

        return null;
    }

    private String lookupProfileId(String profileName, String tenantId) {
        return workerClient.findProfileByName(profileName, tenantId)
                .map(WorkerClient.ProfileInfo::id)
                .orElseGet(() -> {
                    log.warn("Group mapping refers to profile '{}' which is not present in tenant {}",
                            profileName, tenantId);
                    return null;
                });
    }

    private String extractFirstName(OidcUser oidcUser, String displayName) {
        Object givenName = oidcUser.getClaim("given_name");
        if (givenName instanceof String str && !str.isBlank()) {
            return str;
        }
        if (displayName != null && displayName.contains(" ")) {
            return displayName.substring(0, displayName.indexOf(' '));
        }
        return displayName;
    }

    private String extractLastName(OidcUser oidcUser, String displayName) {
        Object familyName = oidcUser.getClaim("family_name");
        if (familyName instanceof String str && !str.isBlank()) {
            return str;
        }
        if (displayName != null && displayName.contains(" ")) {
            return displayName.substring(displayName.indexOf(' ') + 1);
        }
        return null;
    }
}
