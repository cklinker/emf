# Codebase Concerns

## Security Risks

**SQL Injection in SupersetDatabaseUserService:**
- Direct SQL string concatenation with `String.format()` for user-controlled input
- File: `kelta-worker/.../service/SupersetDatabaseUserService.java` (lines 56-99)
- Fix: Use parameterized queries or stored procedures

**Gateway permitAll() Config:**
- `SecurityConfig` permits all exchanges; authorization depends entirely on gateway filters
- File: `kelta-gateway/.../config/SecurityConfig.java` (line 101)
- Fix: Defense-in-depth with explicit route authorization at service level

**Missing Auth on Internal Endpoints:**
- Worker controllers lack `@PreAuthorize`/`@Secured`; vulnerable if accessed directly
- Files: `InternalBootstrapController`, `AuthorizationTestController`, `MetricsController`
- Fix: Add authorization annotations to controller methods

**FlowConfig Schema Creation:**
- Minimal sanitization for dynamic schema names using regex
- File: `kelta-worker/.../config/FlowConfig.java` (lines 112-115)
- Fix: Validate tenant slug format at creation time

## Known Bugs

| Bug | File | Root Cause |
|-----|------|-----------|
| Federated users stuck as PENDING_ACTIVATION | `kelta-auth/.../federation/FederatedUserMapper.java` (174-178) | `lookupProfileId()` returns null (TODO: unimplemented) |
| Password reset sends no email | `kelta-auth/.../controller/PasswordController.java` (96) | Email sending TODO unimplemented |
| Unhandled EmptyResultDataAccessException on user not found | `PasswordController.java` (55-60) | Missing try-catch on `queryForObject()` |
| NPE if `reset_token_expires_at` is NULL | `PasswordController.java` (118-119) | Missing null check before Timestamp cast |

## Tech Debt

**Large files needing decomposition:**
| File | Lines | Fix |
|------|-------|-----|
| `SystemCollectionDefinitions.java` | 1,434 | Move to JSON/YAML config loaded at startup |
| `DynamicCollectionRouter.java` | 1,357 | Extract RouteResolutionHandler, InclusionHandler, FieldMapperHandler |
| `PhysicalTableStorageAdapter.java` | 1,091 | Extract SQL builder classes; separate concerns |

**Other:**
- Hardcoded `emf_control_plane` DB name in `SupersetDatabaseUserService.java` (line 78)
- Potential N+1 in `SearchIndexService.java` during reindex (per-collection queries)

## Fragile Areas

- **FK constraint generation** (`PhysicalTableStorageAdapter.java` 177-185): String concatenation for FK names; collision risk
- **Tenant schema isolation** (`PhysicalTableStorageAdapter.java` 105-107): Assumes schema exists; silent failure on permission error

## Dependency Risks

- Multiple BOM version overrides in worker POM increase transitive conflict risk (`kelta-worker/pom.xml`)

## Postgres max_connections sizing

**Current state (homelab):**
- Single Postgres at `192.168.0.5:5432`, default `max_connections = 100`.
- Per-pod HikariCP defaults after [#917](https://github.com/cklinker/emf/pull/917): worker max=30, auth max=15, ai max=15.
- Cerbos has its own pool against the same Postgres (`cerbos:cerbos` DB, connection details in `emf/cerbos-configmap.yaml`).

**Connection budget at scale:**

| Service | Pool max | Typical replicas | Connections |
|---------|---------:|-----------------:|------------:|
| kelta-worker | 30 | 2-3 | 60-90 |
| kelta-auth   | 15 | 1-2 | 15-30 |
| kelta-ai     | 15 | 1-2 | 15-30 |
| cerbos       | ~10 | 1 | ~10 |
| **Subtotal** | | | **100-160** |
| Migrations PreSync (transient) | 10 | 1 | 10 |
| Headroom for psql / admin | | | 10 |
| **Required** | | | **120-180** |

**Recommendations:**
1. **Immediate**: raise `max_connections` to `200` on `192.168.0.5` (`ALTER SYSTEM SET max_connections = 200; SELECT pg_reload_conf();`). Each connection costs ~10 MB shared memory; 200 × 10 MB = 2 GB. Verify Postgres host has the headroom.
2. **After PgBouncer ships ([homelab-argo#94](https://github.com/cklinker/homelab-argo/pull/94))**: services connect to PgBouncer (which fans in via session pool). Real Postgres connections drop to PgBouncer's `default_pool_size = 60` × 1 connection per logical DB = 60. `max_connections = 100` then becomes enough again.
3. **Long-term**: move Postgres to a dedicated host or managed service with `max_connections = 500+` once tenant count grows past 1k.

**Followup**: alert on `pg_stat_database.numbackends / max_connections > 0.8` via the Prometheus rules in [homelab-argo#93](https://github.com/cklinker/homelab-argo/pull/93). Requires `postgres_exporter` to be deployed alongside Postgres — not yet in homelab-argo.

## Connection Pooler Compatibility

**RLS tenant variable is session-scoped, not transaction-scoped:**
- `TenantAwareDataSourceConfig` issues `SET app.current_tenant_id = '<id>'` on every connection borrowed from HikariCP. This is a Postgres **session** setting that persists until the connection is closed or reset.
- File: `kelta-worker/src/main/java/io/kelta/worker/config/TenantAwareDataSourceConfig.java` (line 89)
- Also: `kelta-ai/src/main/resources/application.yml` (`hikari.connection-init-sql: SET app.current_tenant_id = ''`)

**Impact under PgBouncer transaction-pool mode:**
- PgBouncer's `pool_mode = transaction` returns a different physical Postgres connection per transaction. Session state set on a prior transaction is lost — RLS `current_setting('app.current_tenant_id', true)` returns the empty default, and the `admin_bypass` policy hits, returning rows for **all tenants**.
- This is a tenant-isolation correctness bug, not just a perf issue.

**Required configuration when deploying PgBouncer:**
- Set `pool_mode = session` for all kelta workloads (worker, auth, ai). Lower pooling efficiency than transaction mode but preserves session state. Or:
- Migrate every tenant-scoped DB callsite to issue `SET LOCAL app.current_tenant_id = '<id>'` inside an explicit transaction (requires refactor of all `JdbcTemplate` auto-commit calls + audit of `@Transactional` boundaries).

**Followup**: dedicated rewrite to `SET LOCAL`-inside-transaction is tracked under Tier-2 DB scaling work.

## Test Coverage Gaps

- Federated user provisioning (`FederatedUserMapper`) — no tests
- Password reset workflow end-to-end — no tests
- Schema migration edge cases (`SchemaMigrationEngine` — 771 lines) — minimal tests
- SQL filter operator mappings in `PhysicalTableStorageAdapter` — no tests
