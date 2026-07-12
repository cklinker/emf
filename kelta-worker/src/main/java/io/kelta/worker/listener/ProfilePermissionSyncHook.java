package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.worker.service.CerbosPolicySyncCoalescer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Before-save hook that triggers Cerbos policy sync when a profile's
 * object-level or system-level permissions change.
 *
 * <p>Grants are edited via the {@code profile-object-permissions} and
 * {@code profile-system-permissions} system collections, not the
 * {@code profiles} collection — so {@link CerbosPolicySyncHook} never fired
 * for them and, before this hook existed, a permission change did not reach
 * Cerbos until some unrelated profile save. That cut both ways and the
 * revocation direction is the dangerous one: deleting a permission row left
 * the old grant live in the PDP indefinitely.
 *
 * <p>Registered once per collection (a {@link BeforeSaveHook} targets a single
 * collection name). Syncs go through {@link CerbosPolicySyncCoalescer} — a
 * permission matrix is saved one row at a time, and the coalescer debounces
 * the burst into a single serialized sync per tenant. The sync publishes the
 * existing {@code kelta.cerbos.policies.changed.<tenantId>} subject, which
 * every pod consumes to evict its permission caches — the multi-pod broadcast
 * rule is satisfied with no new subject.
 */
public class ProfilePermissionSyncHook implements BeforeSaveHook {

    /** The permission collections this hook is registered for (one instance each). */
    public static final Set<String> PERMISSION_COLLECTIONS = Set.of(
            "profile-object-permissions", "profile-system-permissions");

    private static final Logger log = LoggerFactory.getLogger(ProfilePermissionSyncHook.class);

    private final String collectionName;
    private final CerbosPolicySyncCoalescer syncCoalescer;

    public ProfilePermissionSyncHook(String collectionName,
                                     CerbosPolicySyncCoalescer syncCoalescer) {
        if (!PERMISSION_COLLECTIONS.contains(collectionName)) {
            throw new IllegalArgumentException(
                    "Unsupported permission collection: " + collectionName);
        }
        this.collectionName = collectionName;
        this.syncCoalescer = syncCoalescer;
    }

    @Override
    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public int getOrder() {
        return 100; // Run after validation and audit hooks, like CerbosPolicySyncHook
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        log.debug("{} row created — scheduling Cerbos policy sync for tenant {}",
                collectionName, tenantId);
        syncCoalescer.requestSync(tenantId);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                             Map<String, Object> previous, String tenantId) {
        log.debug("{} row {} updated — scheduling Cerbos policy sync for tenant {}",
                collectionName, id, tenantId);
        syncCoalescer.requestSync(tenantId);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        // Revocation path — before this hook, a deleted grant stayed live in Cerbos.
        log.debug("{} row {} deleted — scheduling Cerbos policy sync for tenant {}",
                collectionName, id, tenantId);
        syncCoalescer.requestSync(tenantId);
    }
}
