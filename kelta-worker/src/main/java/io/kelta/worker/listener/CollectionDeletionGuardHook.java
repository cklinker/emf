package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.service.S3StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Delete guard and storage-cleanup hook for the {@code collections} system collection.
 *
 * <p>V181 gave the 15 child FKs on {@code collection(id)} an {@code ON DELETE CASCADE}
 * (or {@code SET NULL}), which is what finally made a used collection deletable at all —
 * before it, the first record write created a {@code field_history} row and Postgres
 * rejected every subsequent delete with 23503. That cascade has two consequences this
 * hook exists to handle:
 *
 * <ol>
 *   <li><b>Blast radius.</b> The broken FK was accidentally acting as a safety net. Now a
 *       single {@code DELETE /api/collections/{id}} destroys attachments, layouts, reports,
 *       validation rules, field history and record versions. So a delete that would destroy
 *       children is rejected with a 409 naming the counts unless the caller opts in with
 *       {@code ?force=true}.</li>
 *   <li><b>Orphaned S3 objects.</b> {@code file_attachment} and {@code bulk_job} carry
 *       storage keys. A database-level cascade removes the rows and would leak every
 *       underlying object silently. The keys are therefore collected and deleted here in
 *       {@link #beforeDelete}, because once the cascade fires the keys are unrecoverable —
 *       the same constraint documented on {@link AttachmentCleanupHook}, which covers the
 *       parent-<em>record</em> case and never sees a collection delete.</li>
 * </ol>
 *
 * <p>Calls with no HTTP request context (flows, NATS listeners, schedulers, tests) cannot
 * pass {@code force}. They are treated as <b>not</b> forced, so an automated caller can
 * never destroy authored metadata by accident; such a delete fails closed with the same 409.
 *
 * @since 1.0.0
 */
public class CollectionDeletionGuardHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(CollectionDeletionGuardHook.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private static final String COLLECTIONS = "collections";
    private static final String FORCE_PARAM = "force";

    /**
     * A child table destroyed by the V181 cascade.
     *
     * <p>The FK column is <b>not</b> uniformly {@code collection_id} and the tables are not
     * uniformly tenant-scoped — {@code report} keys on {@code primary_collection_id},
     * {@code layout_related_list} on {@code related_collection_id}, and
     * {@code script_trigger} has no {@code tenant_id} column at all. Getting any of these
     * wrong throws, degrades the count to zero, and silently drops that table from the
     * confirmation message. {@code CollectionDeletionGuardSchemaTest} pins every entry
     * against the shipped migrations.
     *
     * @param label human-facing noun used in the 409 message
     * @param table physical table name
     * @param collectionColumn the FK column referencing {@code collection(id)}
     * @param tenantScoped whether the table carries a {@code tenant_id} column
     */
    record ChildTable(String label, String table, String collectionColumn, boolean tenantScoped) {
    }

    /**
     * Child tables counted for the force guard, in report order.
     *
     * <p>Every table here is one that V181 gave {@code ON DELETE CASCADE} — a table with
     * {@code SET NULL} (email_template) is deliberately absent, since nothing is destroyed.
     */
    static final List<ChildTable> CASCADING_CHILDREN = List.of(
            new ChildTable("attachments", "file_attachment", "collection_id", true),
            new ChildTable("reports", "report", "primary_collection_id", true),
            new ChildTable("page layouts", "page_layout", "collection_id", true),
            new ChildTable("list views", "list_view", "collection_id", true),
            new ChildTable("validation rules", "validation_rule", "collection_id", true),
            new ChildTable("record types", "record_type", "collection_id", true),
            new ChildTable("approval processes", "approval_process", "collection_id", true),
            new ChildTable("script triggers", "script_trigger", "collection_id", false),
            new ChildTable("notes", "note", "collection_id", true),
            new ChildTable("field history entries", "field_history", "collection_id", true),
            new ChildTable("record versions", "record_version", "collection_id", true),
            new ChildTable("bulk jobs", "bulk_job", "collection_id", true),
            new ChildTable("layout assignments", "layout_assignment", "collection_id", true),
            new ChildTable("related lists", "layout_related_list", "related_collection_id", true));

    /** Storage-bearing children: table → storage-key column. Both are tenant-scoped. */
    static final Map<String, String> STORAGE_KEY_COLUMNS = new LinkedHashMap<>(Map.of(
            "file_attachment", "storage_key",
            "bulk_job", "file_storage_key"));

    private final JdbcTemplate jdbcTemplate;
    private final S3StorageService storageService;

    public CollectionDeletionGuardHook(JdbcTemplate jdbcTemplate, S3StorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
    }

    @Override
    public String getCollectionName() {
        return COLLECTIONS;
    }

    /**
     * Runs early so a rejected delete does no other hook's side work, but after the
     * identity guard at -100.
     */
    @Override
    public int getOrder() {
        return -50;
    }

    @Override
    public BeforeSaveResult beforeDelete(String collectionName, String id, String tenantId) {
        if (!COLLECTIONS.equals(collectionName) || id == null || tenantId == null) {
            return BeforeSaveResult.ok();
        }

        Map<String, Integer> children = countChildren(id, tenantId);
        boolean hasChildren = children.values().stream().anyMatch(c -> c > 0);

        if (hasChildren && !forceRequested()) {
            String detail = children.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(e -> e.getValue() + " " + e.getKey())
                    .collect(Collectors.joining(", "));
            log.info("Blocked unforced delete of collection {} (tenant {}): would destroy {}",
                    id, tenantId, detail);
            return BeforeSaveResult.error(null,
                    "Deleting this collection would permanently destroy " + detail
                            + ". Retry with ?force=true to confirm.");
        }

        if (hasChildren) {
            purgeStorageObjects(id, tenantId);
        }
        return BeforeSaveResult.ok();
    }

    /**
     * Counts rows in each cascading child table for this collection.
     *
     * <p>A table that cannot be counted is reported as zero rather than failing the delete:
     * the guard is a confirmation prompt, not an authorization boundary, and a missing
     * optional table must not make a collection undeletable all over again.
     */
    private Map<String, Integer> countChildren(String collectionId, String tenantId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ChildTable child : CASCADING_CHILDREN) {
            try {
                Integer n = child.tenantScoped()
                        ? jdbcTemplate.queryForObject(countSql(child), Integer.class,
                                collectionId, tenantId)
                        : jdbcTemplate.queryForObject(countSql(child), Integer.class, collectionId);
                counts.put(child.label(), n == null ? 0 : n);
            } catch (Exception e) {
                log.warn("Child count failed for {} on collection {}: {}",
                        child.table(), collectionId, e.getMessage());
                counts.put(child.label(), 0);
            }
        }
        return counts;
    }

    /** Count SQL for one child table; the tenant predicate is omitted when unsupported. */
    static String countSql(ChildTable child) {
        return "SELECT COUNT(*) FROM " + child.table()
                + " WHERE " + child.collectionColumn() + " = ?"
                + (child.tenantScoped() ? " AND tenant_id = ?" : "");
    }

    /**
     * Deletes the S3 objects behind this collection's attachments and bulk jobs.
     *
     * <p>Must run BEFORE the row cascade — afterDelete cannot recover a storage key.
     * A failure to delete one object is logged and skipped: leaking an object is strictly
     * better than aborting a delete the caller already confirmed with {@code force}.
     */
    private void purgeStorageObjects(String collectionId, String tenantId) {
        if (!storageService.isEnabled()) {
            return;
        }
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : STORAGE_KEY_COLUMNS.entrySet()) {
            String sql = "SELECT " + entry.getValue() + " FROM " + entry.getKey()
                    + " WHERE collection_id = ? AND tenant_id = ?";
            try {
                List<String> found = jdbcTemplate.queryForList(
                        sql, String.class, collectionId, tenantId);
                found.stream().filter(k -> k != null && !k.isBlank()).forEach(keys::add);
            } catch (Exception e) {
                log.warn("Storage-key lookup failed for {} on collection {}: {}",
                        entry.getKey(), collectionId, e.getMessage());
            }
        }
        if (keys.isEmpty()) {
            return;
        }

        int deleted = 0;
        for (String key : keys) {
            try {
                storageService.deleteObject(key);
                deleted++;
            } catch (Exception e) {
                log.warn("Failed to delete S3 object '{}' during collection cleanup: {}",
                        key, e.getMessage());
            }
        }
        securityLog.info(
                "security_event=COLLECTION_STORAGE_PURGED tenant={} collection={} objects={}/{}",
                tenantId, collectionId, deleted, keys.size());
    }

    /**
     * True when the caller passed {@code ?force=true} on an HTTP request.
     *
     * <p>No request context means no way to confirm, so this returns false and the delete
     * is blocked — automated callers fail closed.
     */
    private boolean forceRequested() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return false;
        }
        HttpServletRequest request = attrs.getRequest();
        return "true".equalsIgnoreCase(request.getParameter(FORCE_PARAM));
    }
}
