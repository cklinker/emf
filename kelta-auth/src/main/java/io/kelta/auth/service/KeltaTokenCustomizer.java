package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class KeltaTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final Logger log = LoggerFactory.getLogger(KeltaTokenCustomizer.class);

    private final JdbcTemplate jdbcTemplate;

    public KeltaTokenCustomizer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        // Handle client_credentials grant (connected apps / API keys)
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            if (context.getTokenType().getValue().equals("access_token")) {
                customizeClientCredentialsToken(context);
            }
            return;
        }

        // Handle user authentication (authorization_code grant)
        Authentication principal = context.getPrincipal();
        if (principal.getPrincipal() instanceof KeltaUserDetails userDetails) {
            if (context.getTokenType().getValue().equals(OidcParameterNames.ID_TOKEN)) {
                customizeIdToken(context, userDetails);
            } else if (context.getTokenType().getValue().equals("access_token")) {
                customizeAccessToken(context, userDetails);
            }
        }
    }

    /**
     * Enriches client_credentials tokens with connected app metadata.
     * These tokens identify machine clients, not human users.
     */
    private void customizeClientCredentialsToken(JwtEncodingContext context) {
        String clientId = context.getRegisteredClient().getClientId();

        // Look up connected app by client_id to get tenant_id and scopes
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, scopes FROM connected_app WHERE client_id = ? AND active = true",
                clientId
        );

        if (!results.isEmpty()) {
            Map<String, Object> app = results.get(0);
            context.getClaims().claims(claims -> {
                claims.put("tenant_id", app.get("tenant_id"));
                claims.put("connected_app_id", app.get("id"));
                claims.put("auth_method", "api_key");
                Object scopes = app.get("scopes");
                if (scopes != null) {
                    claims.put("app_scopes", scopes.toString());
                }
            });
            log.debug("Enriched client_credentials token for app {} (client_id={})", app.get("id"), clientId);
        } else {
            log.warn("No active connected app found for client_id={}", clientId);
        }
    }

    private void customizeIdToken(JwtEncodingContext context, KeltaUserDetails userDetails) {
        context.getClaims().claims(claims -> {
            claims.put("email", userDetails.getEmail());
            claims.put("name", userDetails.getDisplayName());
            claims.put("preferred_username", userDetails.getEmail());
            claims.put("tenant_id", userDetails.getTenantId());
            claims.put("profile_id", userDetails.getProfileId());
            if (userDetails.getProfileName() != null) {
                claims.put("profile_name", userDetails.getProfileName());
            }
        });
    }

    private void customizeAccessToken(JwtEncodingContext context, KeltaUserDetails userDetails) {
        context.getClaims().claims(claims -> {
            claims.put("email", userDetails.getEmail());
            claims.put("preferred_username", userDetails.getEmail());
            claims.put("tenant_id", userDetails.getTenantId());
            claims.put("profile_id", userDetails.getProfileId());
            if (userDetails.getProfileName() != null) {
                claims.put("profile_name", userDetails.getProfileName());
            }
            // Indicate whether this user authenticated via internal login or SSO
            String authMethod = (userDetails.getPassword() == null || userDetails.getPassword().isEmpty())
                    ? "sso" : "internal";
            claims.put("auth_method", authMethod);
        });
    }
}
