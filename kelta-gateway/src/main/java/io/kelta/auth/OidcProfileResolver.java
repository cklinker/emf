package io.kelta.gateway.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Resolves a user's profile name from their OIDC groups using the
 * {@code groups_profile_mapping} configured on the OIDC provider.
 *
 * <p>Resolution priority:
 * <ol>
 *   <li>"System Administrator" — returned immediately if any group maps to it</li>
 *   <li>First matching group in the mapping</li>
 *   <li>Fallback to "Standard User" if no groups match</li>
 * </ol>
 */
@Component
public class OidcProfileResolver {

    private static final Logger log = LoggerFactory.getLogger(OidcProfileResolver.class);
    private static final String SYSTEM_ADMINISTRATOR = "System Administrator";
    private static final String STANDARD_USER = "Standard User";

    private final ObjectMapper objectMapper;

    public OidcProfileResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves the profile name for the given OIDC groups using the provider's mapping.
     *
     * @param groups the user's OIDC groups from the JWT
     * @param groupsProfileMappingJson the JSON string mapping group names to profile names
     * @return the resolved profile name
     */
    public String resolveProfileName(List<String> groups, String groupsProfileMappingJson) {
        if (groups == null || groups.isEmpty() || groupsProfileMappingJson == null || groupsProfileMappingJson.isBlank()) {
            return STANDARD_USER;
        }

        Map<String, String> mapping;
        try {
            mapping = objectMapper.readValue(groupsProfileMappingJson,
                    new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse groups_profile_mapping: {}", e.getMessage());
            return STANDARD_USER;
        }

        if (mapping.isEmpty()) {
            return STANDARD_USER;
        }

        String resolvedProfile = null;
        for (String group : groups) {
            String profileName = mapping.get(group);
            if (profileName == null) {
                continue;
            }
            if (SYSTEM_ADMINISTRATOR.equals(profileName)) {
                return SYSTEM_ADMINISTRATOR;
            }
            if (resolvedProfile == null) {
                resolvedProfile = profileName;
            }
        }

        return resolvedProfile != null ? resolvedProfile : STANDARD_USER;
    }
}
