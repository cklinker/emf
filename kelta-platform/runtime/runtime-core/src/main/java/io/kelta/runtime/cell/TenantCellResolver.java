package io.kelta.runtime.cell;

/**
 * Resolves the cell (shard) a tenant lives in. Used by routing layers to
 * direct a request to the cell-local stack (gateway / worker / DB / Redis /
 * NATS) for that tenant.
 *
 * <p><b>Why cells.</b> A single-stack deployment hits per-pod and per-DB
 * limits at roughly 1-5K active tenants depending on workload mix. Cells
 * shard tenants across N independent stacks so:
 * <ul>
 *   <li>Adding capacity = adding cells, not scaling one stack horizontally
 *       past Postgres and Redis single-node ceilings.</li>
 *   <li>Blast radius of an incident is one cell, not the platform.</li>
 *   <li>Dedicated cells for paid / enterprise tenants isolate noisy
 *       neighbors from the long tail of free / trial tenants.</li>
 * </ul>
 *
 * <p><b>Current state (Tier 3 skeleton).</b> Every tenant routes to the
 * {@link #DEFAULT_CELL_ID} cell. The {@code tenant.cell_id} column (V137)
 * carries the assignment so flipping a tenant to a different cell is a
 * single UPDATE — but the gateway routing layer doesn't yet read this
 * field. That ships in the next Tier-3 PR.
 *
 * @since 1.0.0
 */
public interface TenantCellResolver {

    /** Cell id every tenant lives in until a multi-cell deployment is wired up. */
    String DEFAULT_CELL_ID = "default";

    /**
     * Returns the cell id for the given tenant. Implementations may cache.
     *
     * @param tenantId the tenant id (UUID string)
     * @return the cell id; {@link #DEFAULT_CELL_ID} for any tenant whose
     *         row is missing or whose cell_id column is null
     */
    String cellFor(String tenantId);
}
