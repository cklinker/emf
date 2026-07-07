package io.kelta.worker.service.delegated;

import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.repository.DelegatedAdminScopeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a caller's <em>effective</em> delegated-administration scope: the union of all active
 * scopes listing the caller, minus any manageable profile that <em>currently</em> grants a
 * privileged permission ({@link PrivilegedPermissions}).
 *
 * <p>The runtime re-filter is deliberate defense in depth: {@code DelegatedAdminScopeValidationHook}
 * already rejects privileged entries at scope-save time, but a profile can be granted
 * {@code MANAGE_USERS} <em>after</em> being scoped — the re-filter makes such profiles silently
 * drop out of every delegated scope (fail-closed) instead of remaining manageable.
 *
 * <p>Everything is read fresh from the database per request — no cache, no NATS. Delegated
 * operations are rare admin actions; correctness beats the saved round-trips.
 */
@Service
public class DelegatedAdminService {

    private static final Logger log = LoggerFactory.getLogger(DelegatedAdminService.class);

    /**
     * A caller's resolved delegation. {@link #NONE} for callers who are not delegated admins.
     */
    public record EffectiveDelegatedScope(boolean delegated,
                                          boolean canCreateUsers,
                                          boolean canDeactivateUsers,
                                          boolean canResetPasswords,
                                          Set<String> manageableProfileIds) {

        public static final EffectiveDelegatedScope NONE =
                new EffectiveDelegatedScope(false, false, false, false, Set.of());

        public boolean canManageProfile(String profileId) {
            return profileId != null && manageableProfileIds.contains(profileId);
        }
    }

    private final DelegatedAdminScopeRepository scopeRepository;
    private final BootstrapRepository bootstrapRepository;
    private final ObjectMapper objectMapper;

    public DelegatedAdminService(DelegatedAdminScopeRepository scopeRepository,
                                 BootstrapRepository bootstrapRepository,
                                 ObjectMapper objectMapper) {
        this.scopeRepository = scopeRepository;
        this.bootstrapRepository = bootstrapRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves the effective scope for the caller identified by email. Returns
     * {@link EffectiveDelegatedScope#NONE} when the user is unknown or no active scope lists them.
     * The returned record also carries the caller's user id via {@link #resolveUserId}.
     */
    public EffectiveDelegatedScope effectiveScope(String callerEmail, String tenantId) {
        Optional<String> callerId = resolveUserId(callerEmail, tenantId);
        if (callerId.isEmpty()) {
            return EffectiveDelegatedScope.NONE;
        }
        return effectiveScopeForUserId(callerId.get(), tenantId);
    }

    /** Resolves a platform user id from email within the tenant. */
    public Optional<String> resolveUserId(String email, String tenantId) {
        if (email == null || email.isBlank() || tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return bootstrapRepository.findUserByEmailAnyStatus(email, tenantId)
                .map(row -> (String) row.get("id"));
    }

    private EffectiveDelegatedScope effectiveScopeForUserId(String userId, String tenantId) {
        List<Map<String, Object>> scopes = scopeRepository.findActiveScopes(tenantId);

        boolean delegated = false;
        boolean canCreate = false;
        boolean canDeactivate = false;
        boolean canReset = false;
        Set<String> profiles = new HashSet<>();

        for (Map<String, Object> scope : scopes) {
            Set<String> members = parseIdArray(scope.get("delegated_user_ids"));
            if (!members.contains(userId)) {
                continue;
            }
            delegated = true;
            canCreate |= Boolean.TRUE.equals(scope.get("can_create_users"));
            canDeactivate |= Boolean.TRUE.equals(scope.get("can_deactivate_users"));
            canReset |= Boolean.TRUE.equals(scope.get("can_reset_passwords"));
            profiles.addAll(parseIdArray(scope.get("manageable_profile_ids")));
        }

        if (!delegated) {
            return EffectiveDelegatedScope.NONE;
        }

        // Fail-closed runtime re-filter: drop profiles that have become privileged since save.
        Set<String> privilegedProfiles = scopeRepository.findPrivilegedProfileIds(profiles);
        if (!privilegedProfiles.isEmpty()) {
            log.warn("Delegated scope for user {} drops now-privileged profiles {}", userId, privilegedProfiles);
            profiles.removeAll(privilegedProfiles);
        }

        return new EffectiveDelegatedScope(true, canCreate, canDeactivate, canReset,
                Set.copyOf(profiles));
    }

    /**
     * Parses a JSONB array of id strings (already normalized to a JSON string by the repository).
     * Malformed or non-array content parses to empty — never throws on tenant data.
     */
    Set<String> parseIdArray(Object json) {
        if (json == null) {
            return Set.of();
        }
        try {
            List<?> parsed = objectMapper.readValue(json.toString(), List.class);
            Set<String> ids = new HashSet<>();
            for (Object value : parsed) {
                if (value instanceof String s && !s.isBlank()) {
                    ids.add(s);
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Unparseable delegated-admin id array: {}", e.getMessage());
            return Set.of();
        }
    }
}
