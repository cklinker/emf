# Codebase Concerns

Living catalog of known risk areas. Resolved items moved to "Resolved" section
at the bottom so reviewers can see what's already been addressed.

## Security Risks

**Power controllers gated only by blanket `API_ACCESS` + RLS (2026-07-02 audit).**
`ReportExecutionController`, `DashboardDataController`, `BulkOperationsController`, and
`PackageController` do **not** enforce a specific system permission in-controller — they
rely on tenant-scoping (RLS) plus the gateway's blanket `API_ACCESS` check and the fact
that their UIs sit behind `VIEW_SETUP` nav. A PAT holder with only `API_ACCESS` can call
them directly. `DataExportController` was the most exfiltration-sensitive (one call →
whole-tenant dump) and is now gated on `VIEW_ALL_DATA` (2026-07-02); the others should get
matching in-controller gates (`MANAGE_REPORTS` for reports/dashboards, `MANAGE_DATA` for
bulk, `CUSTOMIZE_APPLICATION`/`VIEW_SETUP` for packages). Follow-up, not yet done.

**Network-level defense-in-depth** (out of tree):
- Worker, auth, ai pods reachable only via gateway in production. Pod-to-pod
  bypass mitigated by K8s NetworkPolicy + service-mesh mTLS, not by app code.
  Verify on cluster: `kubectl get networkpolicy -n kelta`.

**Tenant IP allowlist — `X-Forwarded-For` trust (accepted trade-off):**
- `TenantIpAllowlistFilter` allows a request when **any** IP in the chain (socket +
  every `X-Forwarded-For` hop + `X-Real-IP`) matches an allowed CIDR. This is
  deliberately topology-resilient but means a non-admin could inject an allowed IP via
  `X-Forwarded-For` to bypass the restriction. Chosen by the tenant/operator; tighten to
  socket-only with `kelta.gateway.ip-allowlist.trust-forwarded-for=false` where the proxy
  hop count is known. The filter is **fail-open** by design (missing config, disabled, or
  `MANAGE_TENANTS` holder → allow), so it hardens access but is not a hard security
  boundary on its own — pair it with the network-level controls above.

## Known Bugs

(No open bugs from the original audit. See Resolved → Bugs.)

## Tech Debt

**Large files needing decomposition:**

| File | Lines | Proposed fix |
|------|------:|--------------|
| `SystemCollectionDefinitions.java` | 1,434 | Move to JSON/YAML config loaded at startup |
| `DynamicCollectionRouter.java` | 1,357 | Extract RouteResolutionHandler, InclusionHandler, FieldMapperHandler |
| `PhysicalTableStorageAdapter.java` | 1,091 | Extract SQL builder classes; separate concerns |

**Other:**

- **Related-list mass-edit is deferred (unified record slice 4).** `RelatedList` inline CRUD does per-row create/edit/delete via the existing JSON:API endpoints. A true mass-edit (spec-optional) would be **N sequential PATCHes**, which consumes per-tenant governor quota and has no atomicity — so it waits on a real bulk write endpoint (a separate 🔴 backend gap), rather than shipping an N-call loop. Deleting a `master_detail` child is a normal delete; deleting the parent cascades server-side (FK CASCADE).
- `PageBuilderPage.tsx` (`kelta-ui/app/src/pages/PageBuilderPage/`) is still large (~1.3k lines) but net-shrank in slices 2a/2b/2c: the per-type render switches, `AVAILABLE_COMPONENTS`/`ComponentPalette`, the `PropertyPanel` blocks, and (2c) the in-file native-HTML5-DnD `Canvas` + drag handlers were extracted to `widgets/*`, `palette/Palette.tsx`, `inspector/*`, and `canvas/*`. Remaining bulk is the page-list table, the page-config form modal, and the editor shell — candidates for extraction if it grows further; not currently blocking.
- **`@dnd-kit/{core,sortable,utilities}` (slice 2c)** is a new `kelta-ui/app` runtime dep, **scoped to the page canvas only** — `PageLayoutsPage`/`MenuBuilderPage`/`FlowDesignerPage` stay on native HTML5 DnD; do NOT mix the two libs in one tree. Watch items: ~30–40 kB gz bundle cost; pointer-DnD is flaky in jsdom (interaction tests drive dnd-kit via the **KeyboardSensor** and assert the resulting tree, not pixels — pointer fidelity is covered post-deploy by Playwright). The `vitest.setup.ts` `ResizeObserver` mock is now a real class because `DndContext` does `new ResizeObserver(...)`.
- **Page save-path silent-drop class (slice 2c — closed for `schemaVersion`).** `mergeConfig` only overlays a key when the `handleSavePage` CALL passes it; widening `mergeConfig`'s accepted keys alone silently drops them. 2c passes the full set `{ components, variables, dataSources, schemaVersion: 2 }`. The invariant — *every page-level sibling must be passed at the call site* — must hold for every future sibling (2d adds `variables`/`dataSources` values, 1h adds `access`). A test asserts the `updateMutation.mutate` payload carries `schemaVersion`.
- **`config.layout` is inert/deprecated legacy (slice 2c).** The create-form `layoutType` select (`page-layout-select`) still writes `config.layout.type` and it round-trips untouched, but the widget tree + per-child `span` now own layout — nothing in the canvas or runtime reads `config.layout`. Removal of the select is deferred (it is also the create-time form field; ripping it out touches the create flow + tests). A future slice may delete it once no page reads `config.layout`.
- Hardcoded `emf_control_plane` DB name in `SupersetDatabaseUserService.java` (line 78) — env-driven via `kelta.worker.superset.database-name` default; cleanup is cosmetic.
- Potential N+1 in `SearchIndexService.java` during reindex (per-collection queries) — harmless under normal load; matters at >1k collections.
- **Email templates have two parallel lookup axes** — `EmailRepository.findTemplateByKey` resolves by the stable `template_key` column (V133 seeded eight `user.*` defaults); `findTemplateByName` resolves by the human-friendly `name` column (V141 seeded `password_reset`, `user_invite`, `welcome`). Both implement the same tenant-override → `'system'`-sentinel fallback, so callers picking the "wrong" axis still work but may land on a different seed row than intended. Pick one canonical axis once the calling code settles, and drop the unused seed set.

## Fragile Areas

- **FK constraint generation** (`PhysicalTableStorageAdapter.java` 177-185): String concatenation for FK names; collision risk with very long tenant slugs + collection names.
- **Tenant schema isolation** (`PhysicalTableStorageAdapter.java` 105-107): Assumes schema exists; silent failure on permission error. Surfaces as confused-state on tenant provisioning failure.

## Dependency Risks

- Multiple BOM version overrides in worker POM increase transitive conflict risk (`kelta-worker/pom.xml`). Audit on every Spring Boot bump.
- **pgvector required for VECTOR fields / semantic search.** `PhysicalTableStorageAdapter.initializeCollection` runs `CREATE EXTENSION IF NOT EXISTS vector` lazily — only when a collection actually has a VECTOR field — and throws an actionable `StorageException` if it can't. Local dev + CI use the `pgvector/pgvector:pg15` image (docker-compose + `KeltaStack` Testcontainers). **The standalone prod Postgres at `192.168.0.5` is plain `postgres:15` and must have the pgvector extension installed** (the `.so` available + the worker's DB role allowed to `CREATE EXTENSION`, or an admin pre-creates it) before any tenant defines a VECTOR field; otherwise that collection's table creation fails with the guidance above. Collections without VECTOR fields are unaffected.
- **Stale `spring-kafka` dependency retired** (Phase 0): `runtime-core/pom.xml` previously declared `spring-kafka`, `spring-kafka-test`, and `testcontainers-kafka` despite the platform using NATS JetStream exclusively. Removed; the misnamed `KafkaRecordEventPublisher` was renamed to `NatsRecordEventPublisher`. The stale event-bus comments and docs that mislabeled NATS as the previous broker have since been corrected to NATS across the codebase.

## Postgres max_connections sizing

**Current state (homelab):**
- Single Postgres at `192.168.0.5:5432`, default `max_connections = 100`.
- Per-pod HikariCP defaults after [#917](https://github.com/cklinker/emf/pull/917): worker max=30, auth max=15, ai max=15.
- Cerbos has its own pool against the same Postgres (`cerbos:cerbos` DB).

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
2. **After PgBouncer ships ([homelab-argo#94](https://github.com/cklinker/homelab-argo/pull/94))**: services connect to PgBouncer (session pool). Real Postgres connections drop to PgBouncer's `default_pool_size = 60` × 1 connection per logical DB = 60. `max_connections = 100` then becomes enough again.
3. **Long-term**: move Postgres to a dedicated host or managed service with `max_connections = 500+` once tenant count grows past 1k.

**Followup**: alert on `pg_stat_database.numbackends / max_connections > 0.8` via the Prometheus rules in [homelab-argo#93](https://github.com/cklinker/homelab-argo/pull/93). Requires `postgres_exporter` to be deployed alongside Postgres — not yet in homelab-argo.

## Connection Pooler Compatibility

**kelta-worker RLS tenant variable is now transaction-scoped (PgBouncer-safe).** ✅ Resolved (Phase 0)

`TenantAwareDataSourceConfig.TenantAwareDataSource` scopes `app.current_tenant_id` per database operation:
- **Tenant-scoped connection** (non-empty tenant in `TenantContext`): if the borrowed connection is in autocommit mode, autocommit is turned off (begins a transaction), `SET LOCAL app.current_tenant_id = '<id>'` is issued, and a thin commit-on-close proxy commits + restores autocommit when the connection is released. If a Spring-managed transaction already owns the connection, only `SET LOCAL` is issued and the proxy defers commit/rollback to the owner (it watches the owner's `commit()`/`rollback()` to avoid a double-commit). Because the variable is set per transaction on the same backend that serves the query, it survives PgBouncer `pool_mode = transaction`. Connection-hold time is unchanged — the connection is released right after the operation, exactly like the previous autocommit model.
- **Admin/bypass connection** (no tenant — Flyway, internal, cross-tenant work): keeps the legacy session `SET app.current_tenant_id = ''`. Empty already maps to `admin_bypass`, so transaction scoping is irrelevant to isolation and these paths are unchanged.

Regression guard: `TenantAwareDataSourceTest` asserts tenant connections use transaction-local `SET LOCAL` (not session `SET`) and commit/restore correctly; the harness `TenantIsolationScenarioTest` exercises real-stack isolation over Postgres + RLS.

**Remaining caveat — kelta-ai:** `kelta-ai/src/main/resources/application.yml` still uses `hikari.connection-init-sql: SET app.current_tenant_id = ''` (a session-level set to the bypass value). kelta-ai runs in admin/bypass mode and filters tenants explicitly in SQL rather than relying on RLS tenant isolation, so it is not a leak vector — but if kelta-ai ever begins relying on RLS for tenant-scoped reads, it must adopt the same transaction-scoped `SET LOCAL` mechanism before being placed behind a transaction-pool. Tracked as a follow-up.

## Test Coverage Gaps

- Schema migration edge cases (`SchemaMigrationEngine` — 771 lines) — minimal tests.
- SQL filter operator mappings in `PhysicalTableStorageAdapter` — no tests.
- Federated user provisioning (`FederatedUserMapper`) — happy path only; group→profile mapping permutations not covered.
- Password reset workflow — change/request/reset all unit-tested; full end-to-end (DB → email → reset link → new password) not.
- `CreateValidationRuleTool` (kelta-mcp) has no `CreateValidationRuleToolTest`, unlike its sibling admin tools — every MCP tool should assert its on-the-wire JSON:API body with WireMock JSON-path matchers.
- **Page-builder action runtime (slice 2e)** — `runtime/executeAction.ts` correctness (esp. the `runFlow` execute→poll loop with `data.id` read + chain-stop-on-reject, and the `assertSafeUrl` `javascript:`/`data:` scheme allow-list) is covered only by Vitest with a **mocked `apiClient`/router/toast** + fake timers. Mocks can hide endpoint-shape drift — exactly the class of bug behind the existing `executionId` vs `data.id` read in the admin flow pages. The real-path guard is the **2e-owned post-deploy Playwright spec** `e2e-tests/tests/page-builder-v2.spec.ts`, which asserts a **persisted record** from a button `onClick` `createRecord` (a real mutation, not just render) — mirroring the "DB-constraint test gap" lesson. It is `test.describe.skip`-gated (the v2 builder admin route is absent until deployed) and must be run against the deployed env before the feature is declared done.

---

## Resolved

### Security (all addressed)

- **SupersetDatabaseUserService SQL injection** — already hardened. `validateSlug`/`validateTenantId`/`validateDatabaseName` + `quoteIdent`/`quoteLiteral` defense-in-depth. See class javadoc at lines 24-42 of `SupersetDatabaseUserService.java`.
- **Gateway `permitAll()` actuator exposure** — `SecurityConfig.securityWebFilterChain` now requires JWT on `/actuator/**` except `/actuator/health` and `/actuator/info` (Kubernetes probes). Custom `GlobalFilter`s still handle API auth.
- **Missing auth on `/internal/**`** — `InternalEndpointSecurityConfig` (flag-gated by `kelta.worker.internal-auth.enabled`) enforces OAuth2 JWT with `scope=internal`. Gateway-side at `InternalServiceAuthConfig` attaches a client-credentials bearer token to outbound internal calls.
- **FlowConfig schema name sanitization** — `SchemaLifecycleModule` validates tenant slug against `^[a-z][a-z0-9-]{0,62}$` and escapes any internal double-quote before CREATE SCHEMA.

### Bugs (all addressed)

- **Federated users stuck PENDING_ACTIVATION** — `FederatedUserMapper.lookupProfileId` (line 199) calls `WorkerClient.findProfileByName` against `/internal/profile/by-name`.
- **Password reset sends no email** — `PasswordController.sendPasswordResetEmail` (line 212) calls `WorkerClient.sendTemplateEmail` with the `user.password_reset` template.
- **Unhandled EmptyResultDataAccessException** — `PasswordController.changePassword` (line 82) catches `EmptyResultDataAccessException` and returns the generic 400 to prevent username enumeration.
- **NPE on null `reset_token_expires_at`** — `PasswordController.resetPassword` (line 185) explicitly null-checks `expiresTs` before the `Timestamp.toInstant()` cast.
