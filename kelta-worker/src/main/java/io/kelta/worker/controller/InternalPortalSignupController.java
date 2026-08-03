package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.PortalUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Internal endpoint backing portal self-signup, called by kelta-auth.
 *
 * <p><b>Why signup goes through the worker at all.</b> kelta-auth shares the
 * database and could insert a {@code platform_user} itself — but
 * {@link PortalUserService#invitePortalUser} already enforces the
 * {@code maxPortalUsers} governor, resolves the Portal User profile, issues the
 * PORTAL_INVITE token and writes the audit record. Public self-signup <b>without
 * a seat governor is unbounded account creation</b>, and reimplementing the
 * governor in kelta-auth (which has no {@code TenantQuotaResolver}) would mean
 * two copies that drift. One enforcement point, reused.
 *
 * <p>Not the existing {@code /api/admin/users/portal-invite}: that requires
 * {@code MANAGE_USERS} and a resolved profile, which an unauthenticated signup
 * has neither of. This is a separate door with a different key — the shared
 * internal token, same as {@code InternalEmailController}. It is <b>not</b>
 * gateway-routed.
 *
 * <p>Returns an outcome rather than an error status for the expected refusals
 * (existing staff account, seat limit): the caller must answer 202 regardless to
 * stay enumeration-safe, and needs to distinguish them only for its audit log.
 */
@RestController
@RequestMapping("/api/internal/portal")
public class InternalPortalSignupController {

    private static final Logger log = LoggerFactory.getLogger(InternalPortalSignupController.class);

    private final PortalUserService portalUserService;
    private final String internalToken;

    public InternalPortalSignupController(PortalUserService portalUserService,
                                          @Value("${kelta.internal.token:}") String internalToken) {
        this.portalUserService = portalUserService;
        this.internalToken = internalToken;
    }

    /** What happened, for the caller's audit trail only. */
    public enum Outcome {
        /** A new portal user was created and sent an invite link. */
        CREATED,
        /** The email already had a portal account; an invite link was re-sent. */
        REINVITED,
        /** The email belongs to internal staff — no portal account was created. */
        STAFF_ACCOUNT_EXISTS,
        /** The tenant's portal seat governor is exhausted. */
        SEAT_LIMIT_REACHED
    }

    public record SignupRequest(String tenantId, String email, String firstName, String lastName) {
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody SignupRequest request) {

        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
            log.warn("Rejected internal portal signup with invalid token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null || request.tenantId() == null || request.tenantId().isBlank()
                || request.email() == null || request.email().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // No ambient tenant on an internal call — bind one so RLS applies to the
        // reads and writes inside PortalUserService.
        Outcome outcome = TenantContext.callWithTenant(request.tenantId(), () -> {
            try {
                PortalUserService.PortalInviteResult result = portalUserService.invitePortalUser(
                        request.tenantId(), "self-signup",
                        request.email(), request.firstName(), request.lastName());
                return result.created() ? Outcome.CREATED : Outcome.REINVITED;
            } catch (ResponseStatusException e) {
                // invitePortalUser signals these as HTTP statuses; translate rather
                // than propagate, because the caller must answer 202 either way.
                if (HttpStatus.CONFLICT.equals(e.getStatusCode())) {
                    return Outcome.STAFF_ACCOUNT_EXISTS;
                }
                if (HttpStatus.TOO_MANY_REQUESTS.equals(e.getStatusCode())) {
                    log.warn("Portal self-signup refused for tenant {} — seat governor exhausted",
                            request.tenantId());
                    return Outcome.SEAT_LIMIT_REACHED;
                }
                throw e;
            }
        });

        return ResponseEntity.ok(Map.of("outcome", outcome.name()));
    }
}
