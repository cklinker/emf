package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

@Component
public class KeltaTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final Logger log = LoggerFactory.getLogger(KeltaTokenCustomizer.class);

    @Override
    public void customize(JwtEncodingContext context) {
        Authentication principal = context.getPrincipal();

        if (principal.getPrincipal() instanceof KeltaUserDetails userDetails) {
            if (context.getTokenType().getValue().equals(OidcParameterNames.ID_TOKEN)) {
                customizeIdToken(context, userDetails);
            } else if (context.getTokenType().getValue().equals("access_token")) {
                customizeAccessToken(context, userDetails);
            }
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
