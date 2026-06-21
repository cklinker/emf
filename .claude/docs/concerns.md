# Codebase Concerns

Living catalog of known risk areas. Resolved items moved to "Resolved" section
at the bottom so reviewers can see what's already been addressed.

## Security Risks

(No open application-layer issues. See Resolved → Security for the prior list.)

**Network-level defense-in-depth** (out of tree):
- Worker, auth, ai pods reachable only via gateway in production. Pod-to-pod
  bypass mitigated by K8s NetworkPolicy + service-mesh mTLS, not by app code.
  Verify on cluster: `kubectl get networkpolicy -n kelta`.

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

- Hardcoded `emf_control_plane` DB name in `SupersetDatabaseUserService.java` (line 78) — env-driven via `kelta.worker.superset.database-name` default; cleanup is cosmetic.
- Potential N+1 in `SearchIndexService.java` during reindex (per-collection queries) — harmless under normal load; matters at >1k collections.
- **Email templates have two parallel lookup axes** — `EmailRepository.findTemplateByKey` resolves by the stable `template_key` column (V133 seeded eight `user.*` defaults); `findTemplateByName` resolves by the human-friendly `name` column (V141 seeded `password_reset`, `user_invite`, `welcome`). Both implement the same tenant-override → `'system'`-sentinel fallback, so callers picking the "wrong" axis still work but may land on a different seed row than intended. Pick one canonical axis once the calling code settles, and drop the unused seed set.
- **Stale `workflow_action_type` catalog rows** — the `workflow_action_type` table is a **display-only catalog**; its `handler_class` column is never read by Java (flow execution dispatches via `ActionHandlerRegistry`, populated by the compile-time modules). Its seed rows still carry dead `com.emf.controlplane.*` class names and describe `PUBLISH_EVENT` as a "Kafka event" despite Kafka being fully removed. Cosmetic, but misleads anyone reading the table — re-seed accurately or drop `handler_class`.

## Fragile Areas

- **FK constraint generation** (`PhysicalTableStorageAdapter.java` 177-185): String concatenation for FK names; collision risk with very long tenant slugs + collection names.
- **Tenant schema isolation** (`PhysicalTableStorageAdapter.java` 105-107): Assumes schema exists; silent failure on permission error. Surfaces as confused-state on tenant provisioning failure.

## Dependency Risks

- Multiple BOM version overrides in worker POM increase transitive conflict risk (`kelta-worker/pom.xml`). Audit on every Spring Boot bump.
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
