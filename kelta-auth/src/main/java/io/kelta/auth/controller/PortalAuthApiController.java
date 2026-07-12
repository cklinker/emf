package io.kelta.auth.controller;

import io.kelta.auth.config.AuthProperties;
import io.kelta.auth.service.AuthDomainResolver;
import io.kelta.auth.service.PortalLoginService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Headless JSON API over the passwordless portal-login machinery (telehealth
 * slice 8, {@code specs/telehealth/8-portal-auth-headless.md}). Lets a
 * third-party frontend (a tenant's own portal site) drive the magic-link flow
 * end-to-end: request a link whose email lands on the external site, then
 * exchange the clicked token for a bearer access token.
 *
 * <p>Reuses {@link PortalLoginService} verbatim — enumeration safety, the
 * per-user rate limit, single-use token consumption, and audit events are the
 * same as the Thymeleaf flow in {@link PortalLoginController}. The minted JWT
 * follows the {@code /auth/direct-login} precedent ({@link DirectLoginController}):
 * signed by the authorization server's {@link JwtEncoder} with the same claims
 * {@code KeltaTokenCustomizer} puts on OIDC access tokens — including
 * {@code user_type}, which the gateway stamps into {@code X-User-Type}.
 */
@RestController
@RequestMapping("/portal/api/login")
public class PortalAuthApiController {

    private static final Logger log = LoggerFactory.getLogger(PortalAuthApiController.class);

    private static final long ACCESS_TOKEN_TTL_HOURS = 8;

    private final PortalLoginService portalLoginService;
    private final AuthDomainResolver domainResolver;
    private final AuthProperties authProperties;
    private final JwtEncoder jwtEncoder;

    public PortalAuthApiController(PortalLoginService portalLoginService,
                                   AuthDomainResolver domainResolver,
                                   AuthProperties authProperties,
                                   JwtEncoder jwtEncoder) {
        this.portalLoginService = portalLoginService;
        this.domainResolver = domainResolver;
        this.authProperties = authProperties;
        this.jwtEncoder = jwtEncoder;
    }

    public record LoginLinkRequest(String email, String tenant, String redirectUri) {}

    public record VerifyRequest(String token) {}

    /**
     * Always answers 202 whether or not the email matches a portal user — the
     * only client-visible failure is a disallowed {@code redirectUri}, which is
     * validated before any user lookup and therefore carries no account signal.
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> requestLink(@RequestBody LoginLinkRequest body,
                                                           HttpServletRequest request) {
        String email = body == null ? null : body.email();
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email_required"));
        }

        String tenant = body.tenant() != null && !body.tenant().isBlank()
                ? body.tenant()
                : domainResolver.resolveTenantSlug(request.getServerName()).orElse(null);
        Optional<String> tenantUuid = portalLoginService.resolveTenantUuid(tenant);

        String linkBase;
        if (body.redirectUri() != null && !body.redirectUri().isBlank()) {
            // Exact-match against the tenant's allowlist. An unknown tenant has
            // an empty allowlist, so it fails the same way — indistinguishable.
            List<String> allowed = tenantUuid
                    .map(portalLoginService::portalRedirectUris)
                    .orElse(List.of());
            if (!allowed.contains(body.redirectUri())) {
                log.warn("Rejected portal login request with non-allowlisted redirectUri for tenant {}", tenant);
                return ResponseEntity.badRequest().body(Map.of("error", "redirect_uri_not_allowed"));
            }
            linkBase = body.redirectUri();
        } else {
            // No redirect requested: the link lands on the classic verify page
            // of the current host, exactly like the form flow.
            linkBase = ServletUriComponentsBuilder.fromRequestUri(request)
                    .replacePath("/portal/login/verify")
                    .replaceQuery(null)
                    .build()
                    .toUriString();
        }

        String base = linkBase;
        tenantUuid.ifPresent(uuid -> portalLoginService.requestLink(uuid, email, base));
        return ResponseEntity.accepted().body(Map.of("status", "ok"));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody VerifyRequest body) {
        String token = body == null ? null : body.token();
        Optional<PortalLoginService.PortalVerification> verified = portalLoginService.verify(token);
        if (verified.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_or_expired_token"));
        }

        var userDetails = verified.get().userDetails();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ACCESS_TOKEN_TTL_HOURS, ChronoUnit.HOURS);

        // Same claim set KeltaTokenCustomizer puts on OIDC access tokens.
        // user_type is load-bearing: the gateway derives X-User-Type from it,
        // and the portal product endpoints authorize on PORTAL.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getIssuerUri())
                .subject(userDetails.getId())
                .audience(List.of("kelta-platform"))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("scope", "openid profile email")
                .claim("email", userDetails.getEmail())
                .claim("preferred_username", userDetails.getEmail())
                .claim("tenant_id", userDetails.getTenantId())
                .claim("profile_id", userDetails.getProfileId())
                .claim("profile_name", userDetails.getProfileName())
                .claim("user_type", userDetails.getUserType())
                .claim("auth_method", "magic_link")
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        log.debug("Headless portal login verified for user {} (tenant {})",
                userDetails.getId(), userDetails.getTenantId());
        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "tokenType", "Bearer",
                "expiresIn", ACCESS_TOKEN_TTL_HOURS * 3600,
                "expiresAt", expiresAt.toString(),
                "tenantSlug", verified.get().tenantSlug(),
                "userId", userDetails.getId()));
    }
}
