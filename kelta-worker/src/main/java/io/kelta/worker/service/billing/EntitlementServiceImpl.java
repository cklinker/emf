package io.kelta.worker.service.billing;

import io.kelta.runtime.module.service.MemberEntitlements;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.BillingPass;
import io.kelta.worker.repository.BillingPassRepository;
import io.kelta.worker.repository.BillingPlan;
import io.kelta.worker.repository.BillingPlanRepository;
import io.kelta.worker.repository.BillingSubscription;
import io.kelta.worker.repository.BillingSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link EntitlementService}.
 *
 * <p><b>Resolution order.</b> The base plan is the member's subscription plan
 * when its status is entitling ({@code active}, {@code trialing},
 * {@code past_due} — see {@link BillingSubscription#ENTITLING_STATUSES}),
 * otherwise the tenant's DEFAULT plan. Every live pass is then merged on top.
 * A lapsed subscription therefore degrades to the free baseline rather than to
 * nothing, and {@code past_due} keeps its entitlements while the processor
 * retries the card — cutting a member off mid-retry punishes a recoverable
 * failure.
 *
 * <p><b>Merge rules.</b> Numbers SUM, booleans OR, arrays union, and anything
 * else is last-write-wins. Passes are additive by construction: buying a
 * ten-watch pass on top of a ten-watch plan gives twenty, and a pass can grant a
 * capability the plan lacks but can never revoke one it has.
 *
 * <p><b>Expiry is read-time.</b> A pass is only merged when
 * {@link BillingPass#isLive(Instant)} holds, regardless of its stored status, so
 * a member is never over-entitled by an expiry sweep that has not run yet.
 *
 * <p>Cached per member with a short TTL and invalidated fleet-wide over NATS
 * (Critical Rule 1); the cache is a latency optimization, never the source of
 * truth.
 */
@Service
public class EntitlementServiceImpl implements EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementServiceImpl.class);

    private final BillingPlanRepository planRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPassRepository passRepository;
    private final ObjectMapper objectMapper;
    private final Cache<String, MemberEntitlements> cache;

    public EntitlementServiceImpl(
            BillingPlanRepository planRepository,
            BillingSubscriptionRepository subscriptionRepository,
            BillingPassRepository passRepository,
            ObjectMapper objectMapper,
            @Value("${kelta.billing.entitlements.cache.ttl-seconds:300}") long ttlSeconds,
            @Value("${kelta.billing.entitlements.cache.max-size:10000}") long maxSize) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passRepository = passRepository;
        this.objectMapper = objectMapper;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public MemberEntitlements resolve(String tenantId, String userId) {
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            return MemberEntitlements.EMPTY;
        }
        return cache.get(cacheKey(tenantId, userId), k -> load(tenantId, userId));
    }

    private MemberEntitlements load(String tenantId, String userId) {
        // RLS-scoped reads: the webhook and sweep paths have no tenant bound, so
        // bind one explicitly rather than relying on ambient context.
        return TenantContext.callWithTenant(tenantId, () -> {
            Optional<BillingSubscription> subscription =
                    subscriptionRepository.findByUserId(tenantId, userId);

            Optional<BillingPlan> basePlan = subscription
                    .filter(BillingSubscription::isEntitling)
                    .flatMap(s -> s.planId() == null
                            ? Optional.empty()
                            : planRepository.findById(tenantId, s.planId()))
                    .or(() -> planRepository.findDefault(tenantId));

            Map<String, Object> merged = new LinkedHashMap<>(
                    basePlan.map(p -> parseEntitlements(p.entitlements())).orElseGet(Map::of));

            Instant now = Instant.now();
            for (BillingPass pass : passRepository.findActiveByUserId(tenantId, userId)) {
                if (!pass.isLive(now) || pass.planId() == null) {
                    continue;
                }
                planRepository.findById(tenantId, pass.planId())
                        .map(p -> parseEntitlements(p.entitlements()))
                        .ifPresent(passValues -> mergeInto(merged, passValues));
            }

            String status = subscription.map(BillingSubscription::status).orElse(null);
            return new MemberEntitlements(
                    basePlan.map(BillingPlan::code).orElse(null),
                    // Only report a status when it is the one that actually
                    // supplied the baseline; a lapsed member is on DEFAULT.
                    subscription.filter(BillingSubscription::isEntitling).isPresent() ? status : null,
                    merged);
        });
    }

    /**
     * Merges {@code addition} into {@code base}: numbers SUM, booleans OR, arrays
     * union (order-preserving, de-duplicated), everything else last-write-wins.
     */
    @SuppressWarnings("unchecked")
    static void mergeInto(Map<String, Object> base, Map<String, Object> addition) {
        addition.forEach((key, value) -> base.merge(key, value, (existing, incoming) -> {
            if (existing instanceof Number a && incoming instanceof Number b) {
                return sum(a, b);
            }
            if (existing instanceof Boolean a && incoming instanceof Boolean b) {
                return a || b;
            }
            if (existing instanceof List<?> a && incoming instanceof List<?> b) {
                LinkedHashSet<Object> union = new LinkedHashSet<>((List<Object>) a);
                union.addAll((List<Object>) b);
                return new ArrayList<>(union);
            }
            return incoming;
        }));
    }

    /** Keeps integral sums integral; falls back to BigDecimal for fractional values. */
    private static Number sum(Number a, Number b) {
        boolean integral = isIntegral(a) && isIntegral(b);
        if (integral) {
            return a.longValue() + b.longValue();
        }
        return new BigDecimal(a.toString()).add(new BigDecimal(b.toString()));
    }

    private static boolean isIntegral(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }

    /**
     * Parses a plan's opaque entitlements JSON. Malformed JSON yields an empty
     * map with a warning rather than an exception: a bad plan row must not take
     * down every entitlement check for the tenant.
     */
    Map<String, Object> parseEntitlements(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return Map.of();
            }
            return objectMapper.convertValue(node, Map.class);
        } catch (RuntimeException e) {
            log.warn("Ignoring unparseable plan entitlements: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public int intLimit(String tenantId, String userId, String key, int deflt) {
        return resolve(tenantId, userId).intValue(key, deflt);
    }

    @Override
    public boolean boolLimit(String tenantId, String userId, String key, boolean deflt) {
        return resolve(tenantId, userId).boolValue(key, deflt);
    }

    @Override
    public List<String> listLimit(String tenantId, String userId, String key) {
        return resolve(tenantId, userId).listValue(key);
    }

    @Override
    public void invalidate(String tenantId, String userId) {
        if (tenantId == null || userId == null) {
            return;
        }
        cache.invalidate(cacheKey(tenantId, userId));
    }

    @Override
    public void invalidateTenant(String tenantId) {
        if (tenantId == null) {
            return;
        }
        String prefix = tenantId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String cacheKey(String tenantId, String userId) {
        return tenantId + ":" + userId;
    }
}
