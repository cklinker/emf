package io.kelta.worker.listener;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.AggregationSpec;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.FilterOperator;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.BillingEntitlementRule;
import io.kelta.worker.service.billing.BillingEntitlementRuleCache;
import io.kelta.worker.service.billing.EntitlementService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enforces per-member record quotas driven by {@code billing-entitlement-rules}
 * rows — "records in collection X are capped by entitlement key Y" — so a tenant
 * caps a new collection by adding a row, not by shipping code.
 *
 * <p><b>This is a wildcard hook: it runs on every record create in the system.</b>
 * The fast path therefore has to cost nothing. A cache hit returning an empty rule
 * list ends the call before any query, and the overwhelmingly common case (tenant
 * has no rules, or this collection is uncapped) hits exactly that.
 *
 * <p><b>Counting.</b> The member's existing rows are counted through
 * {@link QueryEngine#aggregate}, which validates every filter field against the
 * collection definition before building SQL and binds all values as parameters.
 * That is what makes the tenant-authored {@code countFilter} JSON safe: an
 * unknown field name is rejected rather than interpolated.
 *
 * <p><b>Fail-open, deliberately.</b> A missing collection definition, an
 * unparseable filter, or a failed count allows the write and logs. This mirrors
 * the existing governor-limit hooks: a billing-infrastructure glitch must not
 * block a tenant's data entry. It is the opposite of the *guard* hooks, which
 * fail closed — the distinction is that those protect other users' data, while
 * this one protects revenue.
 *
 * <p>Rejections surface as HTTP 400 with JSON:API code {@code beforeSaveHook}
 * (the platform's mapping for a failed before-save hook), carrying an
 * upgrade-oriented message.
 */
public class MemberEntitlementQuotaHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(MemberEntitlementQuotaHook.class);

    /**
     * The field identifying who owns a record. Stamped by the router from the
     * gateway identity on every HTTP create.
     */
    static final String OWNER_FIELD = "createdBy";

    private static final String USER_TYPE_HEADER = "X-User-Type";
    private static final String INTERNAL = "INTERNAL";
    private static final String COUNT_ALIAS = "memberRecordCount";

    private final BillingEntitlementRuleCache ruleCache;
    private final EntitlementService entitlementService;
    private final CollectionRegistry collectionRegistry;
    private final QueryEngine queryEngine;
    private final ObjectMapper objectMapper;

    public MemberEntitlementQuotaHook(BillingEntitlementRuleCache ruleCache,
                                      EntitlementService entitlementService,
                                      CollectionRegistry collectionRegistry,
                                      QueryEngine queryEngine,
                                      ObjectMapper objectMapper) {
        this.ruleCache = ruleCache;
        this.entitlementService = entitlementService;
        this.collectionRegistry = collectionRegistry;
        this.queryEngine = queryEngine;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getCollectionName() {
        return BeforeSaveHookRegistry.WILDCARD;
    }

    /**
     * Early within the wildcard group, so a rejected write does no later side
     * work. Note the registry runs every collection-specific hook before any
     * wildcard hook regardless of this value.
     */
    @Override
    public int getOrder() {
        return -90;
    }

    @Override
    public BeforeSaveResult beforeCreate(String collectionName, Map<String, Object> record,
                                         String tenantId) {
        if (tenantId == null || tenantId.isBlank() || collectionName == null) {
            return BeforeSaveResult.ok();
        }

        List<BillingEntitlementRule> rules = ruleCache.forCollection(tenantId, collectionName);
        if (rules.isEmpty()) {
            return BeforeSaveResult.ok(); // fast path — no query, no allocation
        }

        Object owner = record == null ? null : record.get(OWNER_FIELD);
        if (owner == null || owner.toString().isBlank()) {
            // No actor (flow, scheduler, import). Quotas are a per-member concept,
            // so there is nothing to enforce against.
            return BeforeSaveResult.ok();
        }
        String userId = owner.toString();
        boolean internal = isInternalActor();

        for (BillingEntitlementRule rule : rules) {
            if (internal && BillingEntitlementRule.APPLIES_TO_PORTAL.equals(rule.appliesTo())) {
                continue; // staff are not billed against a member plan
            }
            BeforeSaveResult result = enforce(rule, tenantId, userId, collectionName);
            if (!result.isSuccess()) {
                return result;
            }
        }
        return BeforeSaveResult.ok();
    }

    private BeforeSaveResult enforce(BillingEntitlementRule rule, String tenantId,
                                     String userId, String collectionName) {
        int limit = entitlementService.intLimit(tenantId, userId, rule.limitKey(), Integer.MAX_VALUE);
        if (limit == Integer.MAX_VALUE) {
            // No such entitlement key for this member ⇒ uncapped. A tenant that
            // wants a hard cap must put the key on its DEFAULT plan.
            return BeforeSaveResult.ok();
        }
        if (limit <= 0) {
            return reject(rule, 0, limit);
        }

        CollectionDefinition definition = collectionRegistry.get(collectionName);
        if (definition == null) {
            log.warn("Quota rule {} references collection {} not in the registry — allowing",
                    rule.limitKey(), collectionName);
            return BeforeSaveResult.ok();
        }
        // hasQueryableField, NOT hasField: createdBy is a system audit field that every record
        // carries and the storage layer can filter on, but it is not in the collection's declared
        // fields. hasField therefore returns false for EVERY collection, which made this guard
        // fail open on every positive limit (issue #1384) — only limit <= 0 ever rejected,
        // because that branch short-circuits above.
        if (!definition.hasQueryableField(OWNER_FIELD)) {
            log.warn("Collection {} has no {} field — cannot scope quota {} to a member, allowing",
                    collectionName, OWNER_FIELD, rule.limitKey());
            return BeforeSaveResult.ok();
        }

        List<FilterCondition> filters = new ArrayList<>();
        filters.add(new FilterCondition(OWNER_FIELD, FilterOperator.EQ, userId));
        if (!appendCountFilter(rule, definition, filters)) {
            return BeforeSaveResult.ok(); // unusable filter — logged, fail open
        }

        long used;
        try {
            Object count = queryEngine
                    .aggregate(definition, filters, List.of(new AggregationSpec("COUNT", null, COUNT_ALIAS)))
                    .get(COUNT_ALIAS);
            used = count instanceof Number n ? n.longValue() : 0L;
        } catch (RuntimeException e) {
            log.warn("Quota count failed for {} on {} — allowing: {}",
                    rule.limitKey(), collectionName, e.getMessage());
            return BeforeSaveResult.ok();
        }

        if (used >= limit) {
            return reject(rule, used, limit);
        }
        return BeforeSaveResult.ok();
    }

    /**
     * Appends the rule's {@code countFilter} equality predicates.
     *
     * <p>Field names are checked against the collection definition here <b>and</b>
     * again inside {@code aggregate}; values are bound as parameters, never
     * interpolated. Returns false when the filter is unusable, so the caller can
     * fail open rather than silently enforcing a narrower count than intended.
     */
    private boolean appendCountFilter(BillingEntitlementRule rule, CollectionDefinition definition,
                                      List<FilterCondition> filters) {
        String json = rule.countFilter();
        if (json == null || json.isBlank()) {
            return true;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (RuntimeException e) {
            log.warn("Quota rule {} has unparseable countFilter — allowing: {}",
                    rule.limitKey(), e.getMessage());
            return false;
        }
        if (!node.isObject()) {
            log.warn("Quota rule {} countFilter is not an object — allowing", rule.limitKey());
            return false;
        }

        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String field = entry.getKey();
            // Same reason as the owner guard above: a countFilter may legitimately name an audit
            // field, and hasField would reject it as unknown.
            if (!definition.hasQueryableField(field)) {
                log.warn("Quota rule {} countFilter names unknown field '{}' on {} — allowing",
                        rule.limitKey(), field, definition.name());
                return false;
            }
            filters.add(new FilterCondition(field, FilterOperator.EQ, scalar(entry.getValue())));
        }
        return true;
    }

    private static Object scalar(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        return value.stringValue();
    }

    /**
     * True when the caller is internal staff. Absent header ⇒ INTERNAL, matching
     * the gateway's own default for tokens minted before the claim existed.
     */
    private boolean isInternalActor() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return true; // no HTTP request ⇒ internal tier
        }
        HttpServletRequest request = attrs.getRequest();
        String userType = request.getHeader(USER_TYPE_HEADER);
        return userType == null || userType.isBlank() || INTERNAL.equalsIgnoreCase(userType);
    }

    private BeforeSaveResult reject(BillingEntitlementRule rule, long used, int limit) {
        log.info("Rejecting create — member at quota {} ({}/{})", rule.limitKey(), used, limit);
        String message = rule.message() != null && !rule.message().isBlank()
                ? rule.message()
                : "You have reached your plan limit (" + used + "/" + limit
                        + "). Upgrade your plan to add more.";
        return BeforeSaveResult.error(null, message);
    }
}
