package io.kelta.worker.service.billing;

import io.kelta.runtime.module.service.MemberEntitlements;
import java.util.List;

/**
 * Resolves what a portal member is entitled to, from their subscription and any
 * live one-time passes.
 *
 * <p>Implementations cache per member and are invalidated fleet-wide over NATS
 * whenever a subscription, pass, or plan changes — never rely on a local refresh
 * alone (Critical Rule 1).
 *
 * @since 1.0.0
 */
public interface EntitlementService {

    /**
     * Effective entitlements for a member. Never null — an unknown member, a
     * tenant with no plans, or a lapsed subscription with no DEFAULT plan all
     * resolve to {@link MemberEntitlements#EMPTY} rather than an error, so a
     * caller's fallback path is always the restrictive one.
     */
    MemberEntitlements resolve(String tenantId, String userId);

    /** Integer limit, or {@code deflt} when the member has no such entitlement. */
    int intLimit(String tenantId, String userId, String key, int deflt);

    /** Boolean flag, or {@code deflt} when the member has no such entitlement. */
    boolean boolLimit(String tenantId, String userId, String key, boolean deflt);

    /** String list (e.g. permitted alert channels); empty when unset. */
    List<String> listLimit(String tenantId, String userId, String key);

    /** Drops one member's cached entitlements on this pod. */
    void invalidate(String tenantId, String userId);

    /** Drops every cached entitlement for a tenant (plan or rule change). */
    void invalidateTenant(String tenantId);
}
