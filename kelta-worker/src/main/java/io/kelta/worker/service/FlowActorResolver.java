package io.kelta.worker.service;

import io.kelta.runtime.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves the audit identity ("actor") a flow execution runs as.
 *
 * <p>Precedence: the initiating user when one exists (manual runs,
 * record-change events carry the acting user) → the flow's configured
 * {@code run_as_user_id} → the flow owner ({@code created_by}). The resolved
 * actor is passed to {@link io.kelta.runtime.flow.FlowEngine#startExecution}
 * as the execution user, so action handlers stamp it into
 * {@code createdBy}/{@code updatedBy} on records the flow writes and it is
 * recorded as the execution's {@code started_by}.
 *
 * <p>Non-UUID initiators (the webhook path historically passed the literal
 * {@code "webhook"}) are treated as absent so the fallback chain applies and
 * no non-UUID value reaches a UUID audit column.
 */
@Service
public class FlowActorResolver {

    private static final Logger log = LoggerFactory.getLogger(FlowActorResolver.class);

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final String SELECT_FLOW_ACTOR = """
            SELECT run_as_user_id, created_by FROM flow WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JdbcUserIdResolver userIdResolver;

    public FlowActorResolver(JdbcTemplate jdbcTemplate, JdbcUserIdResolver userIdResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.userIdResolver = userIdResolver;
    }

    /**
     * Resolves the execution actor for a flow start.
     *
     * @param tenantId          tenant the flow belongs to
     * @param flowId            the flow being started
     * @param initiatingUserId  the user who initiated the start, when known
     * @return the actor user id, or {@code null} when nothing resolves
     */
    public String resolve(String tenantId, String flowId, String initiatingUserId) {
        if (initiatingUserId != null && UUID_PATTERN.matcher(initiatingUserId).matches()) {
            return initiatingUserId;
        }
        try {
            // Manual runs may carry an email (gateway auth-code JWTs) — translate
            // it the same way the record router does before falling back.
            if (initiatingUserId != null && !initiatingUserId.isBlank()) {
                String translated = TenantContext.callWithTenant(tenantId,
                    () -> userIdResolver.resolve(initiatingUserId, tenantId));
                if (translated != null && UUID_PATTERN.matcher(translated).matches()) {
                    return translated;
                }
            }
            return TenantContext.callWithTenant(tenantId, () -> {
                List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(SELECT_FLOW_ACTOR, flowId);
                if (rows.isEmpty()) {
                    return null;
                }
                Map<String, Object> row = rows.get(0);
                Object runAs = row.get("run_as_user_id");
                if (runAs != null) {
                    return runAs.toString();
                }
                Object owner = row.get("created_by");
                return owner != null ? owner.toString() : null;
            });
        } catch (Exception e) {
            // Actor resolution must never block a flow start — fall back to no actor.
            log.warn("Could not resolve flow actor for flow {} (tenant {}): {}",
                flowId, tenantId, e.getMessage());
            return null;
        }
    }
}
