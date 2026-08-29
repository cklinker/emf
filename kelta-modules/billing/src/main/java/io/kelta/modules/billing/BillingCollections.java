package io.kelta.modules.billing;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data access over the collections this module declares in its manifest.
 *
 * <p>Everything goes through {@link QueryEngine} against collections created at install time —
 * there is no hand-written SQL and no table this module owns directly, because a module has no DDL
 * capability by design. That is also what makes the data visible in the admin UI's generic
 * list/detail pages for free.
 *
 * <p>Collections are resolved lazily by name on each call rather than cached: a module instance
 * outlives individual requests, and the registry entry is tenant-scoped and can be refreshed by a
 * config broadcast underneath us.
 */
public class BillingCollections {

    static final String PLANS = "billing_plans";
    static final String CUSTOMERS = "billing_customers";
    static final String SUBSCRIPTIONS = "billing_subscriptions";
    static final String PASSES = "billing_passes";

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;

    public BillingCollections(QueryEngine queryEngine, CollectionRegistry collectionRegistry) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
    }

    // ------------------------------------------------------------- Plans

    /** Active plans in display order — backs a pricing page. */
    public List<Map<String, Object>> activePlans() {
        return query(PLANS, List.of(FilterCondition.eq("active", true)), 200);
    }

    public Optional<Map<String, Object>> findPlanByCode(String code) {
        return first(query(PLANS, List.of(FilterCondition.eq("code", code)), 1));
    }

    public Optional<Map<String, Object>> findPlanByStripePriceId(String priceId) {
        return first(query(PLANS, List.of(FilterCondition.eq("stripePriceId", priceId)), 1));
    }

    public Optional<Map<String, Object>> findPlanById(String id) {
        return queryEngine.getById(definition(PLANS), id);
    }

    // ------------------------------------------------------------- Customers

    public Optional<Map<String, Object>> findCustomerByUserId(String userId) {
        return first(query(CUSTOMERS, List.of(FilterCondition.eq("userId", userId)), 1));
    }

    public Optional<Map<String, Object>> findCustomerByStripeId(String stripeCustomerId) {
        return first(query(CUSTOMERS,
                List.of(FilterCondition.eq("stripeCustomerId", stripeCustomerId)), 1));
    }

    /** Records the member-to-customer mapping, tolerating webhook redelivery. */
    public void upsertCustomer(String tenantId, String userId, String stripeCustomerId,
                               String email) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("stripeCustomerId", stripeCustomerId);
        if (email != null) {
            data.put("email", email);
        }

        Optional<Map<String, Object>> existing = findCustomerByUserId(userId);
        if (existing.isPresent()) {
            queryEngine.update(definition(CUSTOMERS),
                    String.valueOf(existing.get().get("id")), data);
        } else {
            data.put("tenantId", tenantId);
            queryEngine.create(definition(CUSTOMERS), data);
        }
    }

    // ------------------------------------------------------------- Subscriptions

    public Optional<Map<String, Object>> findSubscriptionByUserId(String userId) {
        return first(query(SUBSCRIPTIONS, List.of(FilterCondition.eq("userId", userId)), 1));
    }

    public void upsertSubscription(String userId, String planId, String stripeSubscriptionId,
                                   String stripeCustomerId, String status, Instant currentPeriodEnd,
                                   boolean cancelAtPeriodEnd, Instant canceledAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("planId", planId);
        data.put("stripeSubscriptionId", stripeSubscriptionId);
        data.put("stripeCustomerId", stripeCustomerId);
        data.put("status", status);
        data.put("currentPeriodEnd", currentPeriodEnd);
        data.put("cancelAtPeriodEnd", cancelAtPeriodEnd);
        data.put("canceledAt", canceledAt);

        // Keyed on the Stripe subscription id, so a redelivered event updates rather than
        // inserting a duplicate.
        Optional<Map<String, Object>> existing = first(query(SUBSCRIPTIONS,
                List.of(FilterCondition.eq("stripeSubscriptionId", stripeSubscriptionId)), 1));
        if (existing.isPresent()) {
            queryEngine.update(definition(SUBSCRIPTIONS),
                    String.valueOf(existing.get().get("id")), data);
        } else {
            queryEngine.create(definition(SUBSCRIPTIONS), data);
        }
    }

    public void markSubscriptionCanceled(String stripeSubscriptionId, String status,
                                         Instant canceledAt) {
        first(query(SUBSCRIPTIONS,
                List.of(FilterCondition.eq("stripeSubscriptionId", stripeSubscriptionId)), 1))
                .ifPresent(existing -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("status", status);
                    data.put("canceledAt", canceledAt == null ? Instant.now() : canceledAt);
                    queryEngine.update(definition(SUBSCRIPTIONS),
                            String.valueOf(existing.get("id")), data);
                });
    }

    // ------------------------------------------------------------- Passes

    /** True when a pass was already granted for this checkout session. */
    public boolean passExistsForSession(String checkoutSessionId) {
        return !query(PASSES,
                List.of(FilterCondition.eq("stripeCheckoutSessionId", checkoutSessionId)), 1)
                .isEmpty();
    }

    public void grantPass(String userId, String planId, String checkoutSessionId,
                          String paymentIntentId, Instant startsAt, Instant expiresAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("planId", planId);
        data.put("stripeCheckoutSessionId", checkoutSessionId);
        data.put("stripePaymentIntentId", paymentIntentId);
        data.put("status", "ACTIVE");
        data.put("startsAt", startsAt);
        data.put("expiresAt", expiresAt);
        queryEngine.create(definition(PASSES), data);
    }

    /** A member's live passes — expiry is judged at read time, not from stored status. */
    public List<Map<String, Object>> livePasses(String userId, Instant now) {
        return query(PASSES, List.of(
                FilterCondition.eq("userId", userId),
                FilterCondition.eq("status", "ACTIVE")), 100)
                .stream()
                .filter(pass -> {
                    Object expiresAt = pass.get("expiresAt");
                    if (expiresAt == null) {
                        return true; // no expiry set: a perpetual pass
                    }
                    Instant expiry = toInstant(expiresAt);
                    return expiry == null || expiry.isAfter(now);
                })
                .toList();
    }

    // ------------------------------------------------------------- Internals

    private CollectionDefinition definition(String name) {
        CollectionDefinition definition = collectionRegistry.get(name);
        if (definition == null) {
            throw new IllegalStateException(
                    "Billing module collection '" + name + "' is not registered — the module's "
                            + "install-time collection provisioning did not complete");
        }
        return definition;
    }

    private List<Map<String, Object>> query(String collection, List<FilterCondition> filters,
                                            int limit) {
        QueryRequest request = new QueryRequest(
                new Pagination(1, limit), List.of(), List.of(), filters);
        QueryResult result = queryEngine.executeQuery(definition(collection), request);
        return result.data();
    }

    private static Optional<Map<String, Object>> first(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof CharSequence text) {
            try {
                return Instant.parse(text.toString());
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
