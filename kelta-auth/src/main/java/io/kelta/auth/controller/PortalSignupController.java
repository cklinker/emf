package io.kelta.auth.controller;

import io.kelta.auth.service.AuthDomainResolver;
import io.kelta.auth.service.PortalLoginService;
import io.kelta.auth.service.WorkerClient;
import io.kelta.auth.service.botchallenge.BotChallengeVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public portal self-signup (consumer-alerting slice 5).
 *
 * <p><b>Always answers 202</b>, exactly like {@code /portal/api/login/request}.
 * A signup endpoint that distinguished "created" from "already exists" from
 * "that's a staff address" would be an account-enumeration oracle on an
 * unauthenticated path — anyone could probe an email list. The only
 * client-visible failure is a disallowed {@code redirectUri}, which is validated
 * <em>before</em> any account lookup and therefore carries no account signal.
 *
 * <p><b>Off by default.</b> Signup is gated per tenant on
 * {@code tenant.settings.portalAuth.selfSignupEnabled}, which defaults to false —
 * a deploy must not silently open public account creation on every existing
 * invite-only tenant. A disabled tenant is indistinguishable from an unknown one.
 *
 * <p>Account creation is delegated to the worker rather than done here, so the
 * {@code maxPortalUsers} seat governor is enforced: public signup without a seat
 * cap is unbounded account creation. See {@code InternalPortalSignupController}.
 *
 * <p><b>Abuse controls (slice 7).</b> Three independent layers, because none of
 * them is sufficient alone: per-email throttling from {@code PortalLoginService}
 * (bounds mail sent to one address), the Redis-backed per-IP budget in
 * {@code PortalPublicRateLimitFilter} (bounds one source), and the proof-of-work
 * {@code BotChallengeVerifier} plus a honeypot field (bounds a distributed script
 * that stays under every per-IP budget). The challenge is opt-in per deployment.
 * The per-IP budget lives in this service rather than the gateway because
 * {@code /portal/**} has its own ingress and never transits the gateway.
 */
@RestController
@RequestMapping("/portal/api")
public class PortalSignupController {

    private static final Logger log = LoggerFactory.getLogger(PortalSignupController.class);
    private static final Logger audit = LoggerFactory.getLogger("security.audit");

    private final PortalLoginService portalLoginService;
    private final AuthDomainResolver domainResolver;
    private final WorkerClient workerClient;
    private final BotChallengeVerifier botChallengeVerifier;

    public PortalSignupController(PortalLoginService portalLoginService,
                                  AuthDomainResolver domainResolver,
                                  WorkerClient workerClient,
                                  BotChallengeVerifier botChallengeVerifier) {
        this.portalLoginService = portalLoginService;
        this.domainResolver = domainResolver;
        this.workerClient = workerClient;
        this.botChallengeVerifier = botChallengeVerifier;
    }

    /**
     * Signup request. The tenant field is named {@code tenant} rather than the
     * spec's {@code tenantSlug} to match the sibling
     * {@code PortalAuthApiController.LoginLinkRequest} — the same frontend calls
     * both, and two names for one concept is a needless trap.
     */
    public record SignupRequest(String email, String tenant, String redirectUri,
                                String firstName, String lastName,
                                String challenge, String website) {
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequest body,
                                                      HttpServletRequest request) {
        String email = body == null ? null : body.email();
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email_required"));
        }

        // Honeypot: `website` is hidden in the form, so only an automated filler
        // ever populates it. Answer 202 like everything else — an error here
        // would just teach the script which field to leave alone.
        if (body.website() != null && !body.website().isBlank()) {
            audit.info("security_event=PORTAL_SIGNUP actor={} result=ignored detail=honeypot",
                    AuditText.sanitize(email));
            return ResponseEntity.accepted().body(Map.of("status", "ok"));
        }

        // A failed challenge is a plain 400: it is decided before any account
        // lookup, so unlike the 202 path it leaks nothing about the address, and
        // a silent no-op would leave a user with a broken widget stuck with no
        // way to tell.
        BotChallengeVerifier.Result challenge = botChallengeVerifier.verify(body.challenge());
        if (challenge != BotChallengeVerifier.Result.VALID
                && challenge != BotChallengeVerifier.Result.DISABLED) {
            // Audited, not just logged: a distributed script failing the challenge
            // is exactly the signal this control exists to surface, and it would
            // be invisible in an application log nobody alerts on.
            audit.info("security_event=PORTAL_SIGNUP actor={} result=rejected detail=bot_challenge_{}",
                    AuditText.sanitize(email), challenge.name().toLowerCase());
            return ResponseEntity.badRequest().body(Map.of("error", "bot_challenge_failed"));
        }

        String tenant = body.tenant() != null && !body.tenant().isBlank()
                ? body.tenant()
                : domainResolver.resolveTenantSlug(request.getServerName()).orElse(null);
        Optional<String> tenantUuid = portalLoginService.resolveTenantUuid(tenant);

        // Validated before any account lookup, so a rejection says nothing about
        // whether the email or tenant exists. An unknown tenant has an empty
        // allowlist and fails identically.
        if (body.redirectUri() != null && !body.redirectUri().isBlank()) {
            List<String> allowed = tenantUuid
                    .map(portalLoginService::portalRedirectUris)
                    .orElse(List.of());
            if (!allowed.contains(body.redirectUri())) {
                log.warn("Rejected portal signup with non-allowlisted redirectUri for tenant {}",
                        tenant);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "redirect_uri_not_allowed"));
            }
        }

        tenantUuid.ifPresent(uuid -> {
            if (!portalLoginService.selfSignupEnabled(uuid)) {
                // Silent no-op: a disabled tenant must look exactly like an
                // enabled one to an anonymous caller.
                audit.info("security_event=PORTAL_SIGNUP actor={} tenant={} result=ignored "
                        + "detail=self_signup_disabled", AuditText.sanitize(email), uuid);
                return;
            }
            String outcome = workerClient.portalSignup(uuid, email, body.firstName(), body.lastName());
            audit.info("security_event=PORTAL_SIGNUP actor={} tenant={} result={} detail={}",
                    AuditText.sanitize(email), uuid, outcome == null ? "failure" : "success",
                    outcome == null ? "worker_unavailable" : outcome);
        });

        return ResponseEntity.accepted().body(Map.of("status", "ok"));
    }
}
