package io.kelta.worker.controller;

import io.kelta.runtime.module.service.MemberEntitlements;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.repository.BillingPass;
import io.kelta.worker.repository.BillingPassRepository;
import io.kelta.worker.repository.BillingPlan;
import io.kelta.worker.repository.BillingPlanRepository;
import io.kelta.worker.repository.BillingSubscription;
import io.kelta.worker.repository.BillingSubscriptionRepository;
import io.kelta.worker.service.billing.BillingCheckoutService;
import io.kelta.worker.service.billing.EntitlementService;
import io.kelta.worker.interceptor.SelfScopedController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Member-facing billing endpoints.
 *
 * <p>{@code /api/billing/**} is a {@code static-} gateway route, so the gateway
 * only applies the blanket {@code API_ACCESS} check — <b>all</b> member scoping
 * is enforced here.
 *
 * <p>Every endpoint acts on the <b>calling</b> member, resolved from the
 * gateway-stamped {@code X-User-Id}. No endpoint accepts a member id, so there is
 * nothing for a caller to tamper with and no cross-member read to guard against.
 *
 * <p>The webhook lives in {@link BillingWebhookController} because it is
 * unauthenticated and must not share this class's actor resolution.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController implements SelfScopedController {

    private final BillingPlanRepository planRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPassRepository passRepository;
    private final EntitlementService entitlementService;
    private final BillingCheckoutService checkoutService;
    private final UserIdResolver userIdResolver;

    public BillingController(BillingPlanRepository planRepository,
                             BillingSubscriptionRepository subscriptionRepository,
                             BillingPassRepository passRepository,
                             EntitlementService entitlementService,
                             BillingCheckoutService checkoutService,
                             UserIdResolver userIdResolver) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passRepository = passRepository;
        this.entitlementService = entitlementService;
        this.checkoutService = checkoutService;
        this.userIdResolver = userIdResolver;
    }

    /**
     * Active plans, safe fields only — this backs a pricing page, so it must not
     * leak processor ids or the raw entitlement map.
     */
    @GetMapping("/plans")
    public Map<String, Object> listPlans() {
        String tenantId = requireTenant();
        List<Map<String, Object>> plans = new ArrayList<>();
        for (BillingPlan plan : planRepository.findActive(tenantId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", plan.code());
            item.put("name", plan.name());
            item.put("kind", plan.kind());
            item.put("passDurationDays", plan.passDurationDays());
            plans.add(item);
        }
        return Map.of("data", plans);
    }

    /** The calling member's plan, subscription, live passes, and entitlements. */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        String tenantId = requireTenant();
        String userId = requireActor(request, tenantId);

        MemberEntitlements entitlements = entitlementService.resolve(tenantId, userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan", planSummary(tenantId, entitlements));
        body.put("subscription", subscriptionRepository.findByUserId(tenantId, userId)
                .map(BillingController::subscriptionSummary)
                .orElse(null));

        Instant now = Instant.now();
        List<Map<String, Object>> passes = new ArrayList<>();
        for (BillingPass pass : passRepository.findActiveByUserId(tenantId, userId)) {
            if (!pass.isLive(now)) {
                continue; // read-time expiry, same rule the resolver applies
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("planCode", pass.planId() == null ? null
                    : planRepository.findById(tenantId, pass.planId())
                            .map(BillingPlan::code).orElse(null));
            item.put("expiresAt", pass.expiresAt());
            passes.add(item);
        }
        body.put("passes", passes);
        body.put("entitlements", entitlements.values());
        return body;
    }

    /** Starts a hosted checkout and returns the URL to redirect the member to. */
    @PostMapping("/checkout-sessions")
    public Map<String, String> createCheckoutSession(@RequestBody CheckoutSessionRequest body,
                                                     HttpServletRequest request) {
        String tenantId = requireTenant();
        String userId = requireActor(request, tenantId);
        requireText(body == null ? null : body.planCode(), "planCode");
        requireText(body.successUrl(), "successUrl");
        requireText(body.cancelUrl(), "cancelUrl");

        String url = checkoutService.createCheckoutSession(tenantId, userId,
                body.planCode(), body.successUrl(), body.cancelUrl());
        return Map.of("url", url);
    }

    /** Opens the processor's billing portal for the calling member. */
    @PostMapping("/portal-sessions")
    public Map<String, String> createPortalSession(@RequestBody PortalSessionRequest body,
                                                   HttpServletRequest request) {
        String tenantId = requireTenant();
        String userId = requireActor(request, tenantId);
        requireText(body == null ? null : body.returnUrl(), "returnUrl");

        return Map.of("url", checkoutService.createPortalSession(tenantId, userId, body.returnUrl()));
    }

    // ------------------------------------------------------------- Helpers

    private Map<String, Object> planSummary(String tenantId, MemberEntitlements entitlements) {
        if (entitlements.planCode() == null) {
            return null;
        }
        Optional<BillingPlan> plan = planRepository.findByCode(tenantId, entitlements.planCode());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("code", entitlements.planCode());
        summary.put("name", plan.map(BillingPlan::name).orElse(entitlements.planCode()));
        summary.put("kind", plan.map(BillingPlan::kind).orElse(null));
        return summary;
    }

    private static Map<String, Object> subscriptionSummary(BillingSubscription subscription) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", subscription.status());
        summary.put("currentPeriodEnd", subscription.currentPeriodEnd());
        summary.put("cancelAtPeriodEnd", subscription.cancelAtPeriodEnd());
        // Processor ids stay server-side — a member has no use for them and they
        // are useful to an attacker probing the processor account.
        return summary;
    }

    private String requireTenant() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tenant context");
        }
        return tenantId;
    }

    /**
     * Resolves the calling member. The gateway strips client-supplied
     * {@code X-User-Id} at the chain head and re-stamps it only for an
     * authenticated principal, so its presence is a gateway assertion.
     */
    private String requireActor(HttpServletRequest request, String tenantId) {
        String identifier = request.getHeader("X-User-Id");
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        String userId = userIdResolver.resolve(identifier, tenantId);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unresolvable identity");
        }
        return userId;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }

    /** Request body for {@code POST /checkout-sessions}. */
    public record CheckoutSessionRequest(String planCode, String successUrl, String cancelUrl) {
    }

    /** Request body for {@code POST /portal-sessions}. */
    public record PortalSessionRequest(String returnUrl) {
    }
}
