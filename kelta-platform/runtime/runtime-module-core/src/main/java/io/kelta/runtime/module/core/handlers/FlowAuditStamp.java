package io.kelta.runtime.module.core.handlers;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Stamps {@code createdBy}/{@code updatedBy} audit fields on record data
 * written by flow action handlers.
 *
 * <p>The HTTP router stamps these from the gateway's {@code X-User-Id} header,
 * but flow writes bypass the router — before this, every record created or
 * updated by a flow carried {@code null} audit users. The flow start sites
 * resolve an actor (initiating user → flow {@code runAsUserId} → flow owner)
 * into {@code ActionContext.userId}; this helper applies it.
 *
 * <p>Only UUID-shaped actors are stamped: legacy provenance markers (the
 * webhook path historically passed the literal {@code "webhook"}) must never
 * reach a UUID column. Explicit field mappings win — an already-present key is
 * left untouched.
 */
final class FlowAuditStamp {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private FlowAuditStamp() {
    }

    /** True when the actor can be stored in the audit UUID columns. */
    static boolean isStampable(String userId) {
        return userId != null && UUID_PATTERN.matcher(userId).matches();
    }

    /** Stamps both audit fields for a newly created record. */
    static void stampCreate(Map<String, Object> recordData, String userId) {
        if (!isStampable(userId)) {
            return;
        }
        recordData.putIfAbsent("createdBy", userId);
        recordData.putIfAbsent("updatedBy", userId);
    }

    /** Stamps the updater for a record update. */
    static void stampUpdate(Map<String, Object> updateData, String userId) {
        if (!isStampable(userId)) {
            return;
        }
        updateData.putIfAbsent("updatedBy", userId);
    }
}
