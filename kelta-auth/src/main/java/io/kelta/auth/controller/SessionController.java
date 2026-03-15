package io.kelta.auth.controller;

import io.kelta.auth.config.AuthProperties;
import io.kelta.auth.model.KeltaSession;
import io.kelta.auth.service.ExternalTokenValidator;
import io.kelta.auth.service.SessionService;
import io.kelta.auth.service.WorkerClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/session")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);
    static final String SESSION_COOKIE_NAME = "kelta_session";

    private final SessionService sessionService;
    private final ExternalTokenValidator tokenValidator;
    private final WorkerClient workerClient;
    private final AuthProperties properties;

    public SessionController(SessionService sessionService,
                             ExternalTokenValidator tokenValidator,
                             WorkerClient workerClient,
                             AuthProperties properties) {
        this.sessionService = sessionService;
        this.tokenValidator = tokenValidator;
        this.workerClient = workerClient;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createSession(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Tenant-Slug", required = false) String tenantSlug,
            HttpServletResponse response) {

        String token = extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Authorization header"));
        }

        // Validate the external OIDC token
        var validatedToken = tokenValidator.validate(token);
        if (validatedToken.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }

        var tokenInfo = validatedToken.get();

        // Look up user identity from worker
        String resolvedTenantId = tenantId != null ? tenantId : tokenInfo.tenantId();
        String profileId = null;
        String profileName = null;

        if (resolvedTenantId != null) {
            var identity = workerClient.findUserIdentity(tokenInfo.email(), resolvedTenantId);
            if (identity.isPresent()) {
                profileId = identity.get().profileId();
                profileName = identity.get().profileName();
            }
        }

        // Create the SSO session
        KeltaSession session = KeltaSession.builder()
                .email(tokenInfo.email())
                .tenantId(resolvedTenantId)
                .tenantSlug(tenantSlug)
                .profileId(profileId)
                .profileName(profileName)
                .displayName(tokenInfo.displayName())
                .groups(List.of())
                .authSource("external")
                .build();

        String sessionId = sessionService.createSession(session);

        // Set SSO cookie
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sessionId);
        cookie.setDomain(properties.getCookieDomain());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(!"localhost".equals(properties.getCookieDomain()));
        cookie.setMaxAge((int) java.time.Duration.ofHours(8).toSeconds());
        response.addCookie(cookie);

        log.info("Created SSO session for user {} (tenant: {})", tokenInfo.email(), resolvedTenantId);

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteSession(
            @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId,
            HttpServletResponse response) {

        if (sessionId != null) {
            sessionService.deleteSession(sessionId);
        }

        // Clear the cookie
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, "");
        cookie.setDomain(properties.getCookieDomain());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.noContent().build();
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
