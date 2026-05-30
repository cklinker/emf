# Cell-based architecture (Tier 3)

## Why cells

A single-stack deployment — one gateway + one worker + one auth + one ai
serving every tenant — has hard ceilings:

| Resource | Ceiling | Reached at |
|----------|--------:|-----------:|
| Postgres `max_connections` (single primary) | 500-1k | ~3-5 worker pods × 30 connections + auth + ai + cerbos |
| Postgres write throughput (single primary) | 5-10 KTPS sustained | ~1k active tenants writing concurrently |
| Redis single-node ops/sec | ~100k | gateway rate limit + cache invalidation traffic at ~50 RPS/tenant × 1k tenants |
| NATS JetStream subjects | O(subjects × consumers) memory | ~10k subjects (per-tenant per-collection record events) |
| Cerbos PDP CPU | ~10k decisions/sec single instance | tail of large tenant write load |

Cells shard tenants across N independent stacks so each one is below
those single-node ceilings. Adding capacity = adding cells. Blast radius
of an incident = one cell.

## What a cell is

```
                          ┌──────────────────────────────────────────┐
                          │ Routing layer                            │
                          │ (Traefik / ingress NGINX)                │
                          │ Reads X-Tenant-Slug, looks up tenant→cell│
                          │ via TenantCellResolver, routes to:       │
                          └────────┬────────────────────┬────────────┘
                                   │                    │
                ┌──────────────────┴───┐    ┌───────────┴──────────────┐
                │ Cell "default"       │    │ Cell "enterprise-1"      │
                │  - gateway           │    │  - gateway               │
                │  - worker (N pods)   │    │  - worker (N pods)       │
                │  - auth              │    │  - auth                  │
                │  - ai                │    │  - ai                    │
                │  - cerbos            │    │  - cerbos                │
                │  - postgres-default  │    │  - postgres-enterprise-1 │
                │  - redis-default     │    │  - redis-enterprise-1    │
                │  - nats-default      │    │  - nats-enterprise-1     │
                └──────────────────────┘    └──────────────────────────┘
```

Each cell is a complete platform stack. Tenants are pinned to a single
cell — there is no cross-cell query path. Operator moves a tenant to a
new cell by:

1. Stop accepting new writes from the tenant (governor `apiCallsPerDay=0`).
2. Snapshot the tenant's rows from the source cell's Postgres.
3. Restore into the destination cell's Postgres.
4. Set `tenant.cell_id` to the new cell.
5. Re-open writes.

(A live-migration tool ships in a later phase. For now, plan is
overnight maintenance windows per tenant.)

## What's in place today (skeleton)

- **V137 migration** adds `tenant.cell_id VARCHAR(64) NOT NULL DEFAULT 'default'` plus an index on the column. Every existing tenant gets `cell_id = 'default'`.
- **TenantCellResolver interface** in `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/cell/TenantCellResolver.java`.
- **JdbcTenantCellResolver** in the same package — reads `tenant.cell_id`, caches indefinitely in process, fail-open to `DEFAULT_CELL_ID` on DB error.
- **8 unit tests** for the JDBC resolver: default-on-blank, cache hits, missing row, null value, DB error fail-open, invalidate one, invalidate all.

The runtime does not yet read `cell_id` for routing decisions — see "Not yet shipped" below.

## Not yet shipped (follow-up PRs)

1. **Gateway routing layer**: when more than one cell exists, ingress (Traefik or NGINX) needs to read `X-Tenant-Slug` from the incoming request, hit a tenant→cell lookup service (the `TenantCellResolver`), and route to the correct gateway. Today the gateway runs as a single deployment per cluster; we need either a router-in-front-of-gateways or a header-aware gateway that proxies to other gateways.
2. **Cell-aware ArgoCD apps**: every cell needs its own gateway / worker / auth / ai / DB / Redis / NATS Helm release. Today `homelab-argo/emf/` ships one of each. A `homelab-argo/cells/default/` + `homelab-argo/cells/enterprise-1/` split is the cell-app pattern.
3. **NATS subject scoping per cell**: `kelta.config.collection.changed.<tenantId>` and `kelta.record.changed.{tenantId}.{collection}` should also include the cell id (`kelta.cell.default.config.collection.changed.<tenantId>`) so cells do not subscribe to each other's events.
4. **Per-cell observability**: each cell labels its metrics/traces with `cell=<id>` so Grafana can break down per-cell load. Add `OTEL_RESOURCE_ATTRIBUTES=cell=default` to each cell's deployment env.
5. **Cell rebalancing tool**: operator-driven snapshot/restore between cells, or a streaming logical-replication path.
6. **NATS-driven cache invalidation** on `JdbcTenantCellResolver`: when a tenant's cell assignment changes, broadcast on a NATS subject and call `invalidate(tenantId)` on every pod's resolver.

## Migration strategy

- **Phase 0 (now)**: skeleton landed. Everyone in cell `default`. No behavioral change.
- **Phase 1**: ship cell-aware routing layer in the homelab-argo repo with one cell still in use. Validate the routing path with all traffic still hitting `default`.
- **Phase 2**: stand up `enterprise-1` cell. Move one volunteer tenant. Run dual-cell for a week before adding more.
- **Phase 3**: cell auto-assignment policy (tier-based: FREE/PROFESSIONAL → `default`, ENTERPRISE/UNLIMITED → dedicated cell). Run for a quarter.
- **Phase 4**: regional cells (`us-east-1`, `eu-west-1`) for data residency.

## Constraints / risks

- **No cross-cell joins.** Every tenant query must execute entirely inside its cell. Code that today JOINs across the `tenant` table from a different DB cannot survive this split. Audit before shipping cell #2.
- **Cell directory is the new single point of failure.** The `tenant → cell` mapping (currently the `tenant.cell_id` column on the cell-zero Postgres) becomes a global dependency. Once we ship multi-cell, this lookup must be cached extensively and replicated. Plan to move it to a small dedicated "cell directory" service or Redis layer that all cells consult.
- **PgBouncer compatibility**: each cell gets its own PgBouncer in front of its Postgres. Session-pool mode applies cell-wide (see `concerns.md` → "Connection Pooler Compatibility").
