package io.kelta.worker.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Answers "can this collection's record-level Cerbos outcome vary per record?"
 *
 * <p>The generated {@code record} resource policy contains only three kinds of rules
 * (see {@code CerbosPolicyGenerator#generateRecordPolicy}): per-collection CRUD mirrors
 * of the object permissions, VIEW_ALL/MODIFY_ALL overrides, and admin-authored custom
 * rules. Only the custom rules can reference record data — without them the decision is
 * identical for every record of the collection, so the per-record batch check (which
 * ships full record payloads to Cerbos, rich text included) can be replaced by a single
 * cached collection-wide check. That batch was expensive enough that a parallel read
 * burst could push checks past their timeout and open the circuit breaker (2026-07-10).
 *
 * <p>Conservative on purpose: <em>any</em> enabled custom rule on the collection —
 * CEL-bearing or not — reinstates the full batch path, and any failure to load or map
 * the rules reports "variant" so authorization never gets weaker than the batch check.
 *
 * <p>The per-tenant rule set is cached (short TTL as a safety net) and evicted by
 * {@code CerbosCacheInvalidationListener} on the same policy-changed NATS event that
 * evicts the field-access cache, so custom-rule edits propagate to every pod.
 */
@Service
public class RecordRuleIndex {

    private static final Logger log = LoggerFactory.getLogger(RecordRuleIndex.class);

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int CACHE_MAX_SIZE = 1_000;

    /** Marks a tenant whose rules could not be safely mapped — everything is variant. */
    static final String ALL_COLLECTIONS = "*";

    private final JdbcTemplate jdbcTemplate;

    // tenantId -> collection refs (raw collection_id values AND resolved API names)
    // that carry at least one enabled custom rule.
    private final Cache<String, Set<String>> variantCollectionsByTenant;

    public RecordRuleIndex(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.variantCollectionsByTenant = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    /**
     * True when record-level decisions for this collection can differ per record —
     * i.e. the caller must run the full per-record batch check. {@code collectionRef}
     * accepts either the collection API name (what the advice extracts from the URL)
     * or the collection UUID.
     */
    public boolean hasRecordVariantRules(String tenantId, String collectionRef) {
        if (tenantId == null || collectionRef == null) {
            return true;
        }
        Set<String> variant = variantCollectionsByTenant.get(tenantId, this::loadVariantCollections);
        if (variant == null) {
            return true;
        }
        return variant.contains(ALL_COLLECTIONS) || variant.contains(collectionRef);
    }

    /** Evicts the tenant's entry — called on the policy-changed NATS event. */
    public void evictTenant(String tenantId) {
        if (tenantId != null) {
            variantCollectionsByTenant.invalidate(tenantId);
        }
    }

    private Set<String> loadVariantCollections(String tenantId) {
        try {
            // ANSI CAST (not ::) so the lookup also runs on H2 in tests.
            String sql = """
                    SELECT pcr.collection_id, c.name
                    FROM profile_custom_rules pcr
                    LEFT JOIN collection c ON CAST(c.id AS VARCHAR(36)) = pcr.collection_id
                    WHERE pcr.tenant_id = ? AND pcr.enabled = true
                    """;
            Set<String> refs = new HashSet<>();
            jdbcTemplate.query(sql, rs -> {
                String collectionId = rs.getString(1);
                String name = rs.getString(2);
                if (collectionId != null) {
                    refs.add(collectionId);
                }
                if (name != null) {
                    refs.add(name);
                } else if (collectionId != null) {
                    // A rule whose collection we cannot resolve by name might still be
                    // matched by the advice under a name we don't know — treat every
                    // collection as variant rather than risk skipping a real rule.
                    refs.add(ALL_COLLECTIONS);
                }
            }, tenantId);
            return refs;
        } catch (Exception e) {
            // Fail towards the full batch check (never weaker authorization). Not
            // cached: Caffeine treats a null load as absent, so the next call retries.
            log.warn("Could not load custom-rule index for tenant {} — keeping per-record checks: {}",
                    tenantId, e.getMessage());
            return null;
        }
    }
}
