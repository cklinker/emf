# Codebase Concerns

Living catalog of known risk areas. Resolved items moved to "Resolved" section
at the bottom so reviewers can see what's already been addressed.

## Security Risks

**`TenantContext.runAsPlatform`/`callAsPlatform` is a silent-data-loss trap (2026-07-04).**
The javadoc claims a `platform_bypass` RLS policy grants access under the `__platform__`
sentinel — **that policy does not exist in any migration**. `TenantAwareDataSource` binds the
sentinel as a literal tenant id, so `tenant_isolation` matches nothing and `admin_bypass`
(empty-string) doesn't apply: every RLS-scoped read returns zero rows and writes are dropped.
The sandbox/promotion services deliberately use explicit `callWithTenant(<uuid>, …)` per hop
instead. Do not introduce new `runAsPlatform` callers until a real `platform_bypass` policy is
migrated onto every RLS table.

**Sandbox environments + metadata promotion are a destructive prod-config surface (V158).**
Guardrails that MUST stay intact:
- In-controller permission gates: `MANAGE_SANDBOXES` on `/api/environments/**` +
  `/api/promotions/**`, `CUSTOMIZE_APPLICATION` on `/api/packages/**`. The gateway static
  routes give these only blanket `API_ACCESS` — removing the controller checks exposes
  whole-tenant config export and destructive promotion to every Standard User.
- Approver ≠ creator + APPROVED-only execute (four-eyes on prod-config mutation).
- Security types (`ROLE`/`POLICY`/`ROUTE_POLICY`/`FIELD_POLICY`) are excluded from promotion —
  a compromised sandbox must not rewrite production authz.
- Sandbox provisioning copies the parent's IP allowlist onto the sandbox tenant and replaces
  the seeded admin's well-known default password with a one-time random secret — the
  IP-allowlist filter is fail-open on missing config, so skipping the copy exposes a clone of
  production config to any IP.
- `RemotePromotionClient` is an SSRF-adjacent surface (`remote_base_url` is admin-supplied):
  scheme allow-list http/https, no userinfo, redirects disabled, bounded timeouts/response,
  PAT only from the credential vault. Private-range IPs are allowed **deliberately**
  (in-cluster/homelab targets are the use case).
- Accepted v1 limitations: per-item import (no global tx — DDL + NATS can't roll back;
  re-runs converge via natural-key upsert); rollback re-imports the pre-promotion target
  snapshot but does not undo deletions/creations; remote targets get no local snapshot or
  rollback; large promotes emit one NATS config event per imported item (sequential — same
  profile as bulk schema authoring); sandbox tenants run the full Svix/Superset hook chain and
  count against platform quotas; ≤60s gateway slug-cache lag after sandbox create (masked by
  the CREATING status while cloning).

**`POST /api/operations` (atomic ops) now enforces per-collection authorization (closed 2026-07-05).**
`AtomicOperationsController` is a static gateway route (blanket `API_ACCESS` only), so the gateway's
per-collection Cerbos verb check never ran on it — previously any API user could batch-write
collections the normal dynamic route would deny. The controller now mirrors that gateway check:
before executing, it maps each operation to a Cerbos action (`add→create`, `update→edit`,
`remove→delete`), resolves the collection's UUID (`CollectionLifecycleManager.getCollectionIdByName`),
and calls the new worker-side `CerbosAuthorizationService.checkCollectionAccess(...)` — the
object-level (`collection` resource, keyed on **UUID**, same as the gateway) verb check. Any denied
operation fails the **whole batch** with 403 before anything executes (atomic); checks are deduped
per (collection, action). **Fail-closed**: a missing identity (no `X-User-Profile-Id`/`X-Cerbos-Scope`)
or a Cerbos circuit-open/error denies. The `IdentityCollectionGuardHook` (identity collections) still
runs underneath as defense-in-depth. **Pre-merge gate:** the harness Cerbos is allow-all, so real
*deny* can only be verified on a live stack (same caveat as the object-policy fix) — unit tests cover
allow/deny/dedup/no-identity with a mocked authz service. Guardrail: keep this check keyed on the
collection **UUID** (`getCollectionIdByName`), not the name — the `collection` policy CEL is
UUID-keyed (see architecture.md "Cerbos collectionId keying").

**Delegated administration is a privilege-boundary surface (V157).** Guardrails that MUST stay
intact: the `DelegatedAdminScopeValidationHook` rejection of privileged profiles/permsets at scope
save, the `DelegatedAdminService.effectiveScope` **request-time re-filter** (a profile granted a
privileged permission after being scoped must silently drop out — do not "optimize" it into a cache
that skips the re-check), the `DelegatedUserAdminController` field whitelist (email/managerId/mfa
immutable, self-edit blocked), and the `IdentityCollectionGuardHook` default-deny for identified
writes. `MANAGE_DELEGATED_ADMINS` rides the platform's "object perms == config perms" posture for
the scope collection's own generic route, same as every other setup collection. Security feature →
**not auto-merged**.

**Mass-email campaigns are a spam-capable, partly-public surface (V152).** Guardrails that
MUST stay intact:
- `CAMPAIGN_TRACKING_SECRET` **must be set to a strong per-deployment value.** It signs the HMAC
  tracking tokens for the public `/api/track/{open,click,unsubscribe}` endpoints. The dev default in
  `CampaignProperties` is a placeholder — if it ships to prod, an attacker can forge unsubscribes and
  poison open/click stats. Inject it via env/secret like `KELTA_ENCRYPTION_KEY`.
- `/api/track/**` is on the gateway `unauthenticated-paths` allowlist (all methods, no JWT). The
  token is the only authenticator — never widen this path or accept an unsigned recipient id.
- The click redirect (`CampaignTrackingController.isSafeUrl`) only forwards to `http(s)` URLs; keep
  that guard (open-redirect / `javascript:` protection).
- Sends are gated on `MANAGE_CAMPAIGNS`, the suppression list (checked per recipient), the
  `campaignEmailsPerDay` governor, and a `send-rate-per-second` throttle. Don't add a bulk-send path
  that bypasses the suppression check or the governor. Campaign writes deliberately do **not** go
  through the generic collection route (the collections are read-only) — keep it that way.

**Power controllers gated only by blanket `API_ACCESS` + RLS (2026-07-02 audit).**
`ReportExecutionController`, `DashboardDataController`, and `PackageController` do
**not** enforce a specific system permission in-controller — they rely on tenant-scoping
(RLS) plus the gateway's blanket `API_ACCESS` check and the fact that their UIs sit
behind `VIEW_SETUP` nav. A PAT holder with only `API_ACCESS` can call them directly.
`DataExportController` was the most exfiltration-sensitive (one call → whole-tenant dump)
and is gated on `VIEW_ALL_DATA` (2026-07-02); **`BulkOperationsController` write
endpoints (create/upload/abort) are now gated on `MANAGE_DATA`** (2026-07-04 — bulk
writes bypass per-record Cerbos advice, so the in-controller gate is the boundary; reads
keep `API_ACCESS`). Remaining follow-up: `MANAGE_REPORTS` for reports/dashboards,
`CUSTOMIZE_APPLICATION`/`VIEW_SETUP` for packages.

**Record merge write path — field-level write-FLS not applied (accepted trade-off).**
`RecordMergeController` (`POST /api/collections/{name}/merge`) applies the master's
`fieldOverrides` and re-parents children by calling the `QueryEngine` **from the service
layer**, so the controller-advice `CerbosFieldWriteSecurityAdvice` (per-field write-FLS) does
**not** run — the same shape as `DuplicateDetectionService`'s read path. The security boundary
is the in-controller `MANAGE_DATA` gate (a bulk data-management authority) plus RLS + record
validation/before-save hooks (which *do* fire through the `QueryEngine`). A `MANAGE_DATA` holder
can therefore set an override on a field they'd be denied at the field level via the normal PATCH
route. Acceptable because merge is an all-or-nothing data-admin operation, but revisit if merge is
ever exposed to a lower-privilege role. It is deliberately **not** auto-invoked by any flow.

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

**Data masking now covers JSON:API + the main egress paths; a few contexts stay v2
(accepted, documented).** Masking (`fieldTypeConfig.masking` + `MASKED` visibility →
`unmask` Cerbos deny) is enforced by `CerbosFieldSecurityAdvice` (strip→mask), the
`MaskedFieldPredicateInterceptor` filter/sort guard, and realtime `data` suppression
(`containsMaskedFields`). **Covered as of PR 2:**
- Data export (`VIEW_ALL_DATA`-gated) and report execution/CSV/PDF export — mask via
  `RecordMaskingService.maskRows` keyed to the requesting user (export threads the
  requester into the async job; reports resolve it in the controller). Reports also
  reject filter/sort/group-by on a field masked for the executor.
- Full-text `search_index` (`display_value` + tsvector) and semantic embeddings —
  masking-configured fields are excluded at index/embed time (field-config level, since
  the shared index has no per-viewer identity), and the collection is reindexed when a
  field's masking config toggles (`FieldConfigEventPublisher` → `rebuildCollectionIndexAsync`).
- Dashboards (`DashboardDataService`) — **now masked per-viewer**: the controller resolves a
  `MaskingPrincipal` from the gateway identity headers (`CerbosPermissionResolver`, same as
  reports). When the widget's target collection has any masking-configured field the shared
  widget cache is **bypassed** (it isn't keyed on the viewer, so a can-unmask user would poison
  it for a can't-unmask one), the table/recent row widgets `maskRows` keyed to the viewer, and
  chart group-by / table sort / any filter on a field masked for the viewer is rejected
  (`WidgetExecutionException`, mirroring reports). Collections with no masking config are
  unchanged (shared cache, no Cerbos). A `null`/system principal is not masked (FLS trust tier).
**Still v2 (emit plaintext / unmasked to their gated callers):**
- Scheduled report delivery + background/system exports run with a `null` principal
  (system trust tier) and are **not** masked — same contract as flows below.
- Flows/scripts/webhooks/NATS consumers — system trust tier, same contract as FLS today.
- Bulk ops / merge — write-side, same accepted trade-off as write-FLS above (their result
  payloads are status/id/counts only, not field values, so no read egress). **Duplicate
  detection is closed (2026-07-05)**: `POST /{collection}/duplicates` returns the match-field
  `values` verbatim, so a match on a field masked for the requester is rejected 400
  (`DuplicateDetectionService` via `MaskingPrincipal` + `RecordMaskingService`) — a grouping key
  can't be masked without collapsing distinct values, so reject (mirrors group-by).
- Stale pre-existing vectors when a source field's masking toggles — **closed (2026-07-05)**.
  `EmbeddingOnWriteHook` already skips embedding a masking-configured source on write (verified);
  the gap was rows embedded *before* masking was added. `FieldConfigEventPublisher` now, on a
  masking-presence toggle, purges the dependent VECTOR columns (`VectorMaintenanceService`
  → `StorageAdapter.clearVectorColumn`, a single `UPDATE … SET vec = NULL` per vector field via
  the adapter's schema/RLS-safe table ref) alongside the existing tsvector reindex, so no stale
  plaintext-derived vector survives to leak via semantic-search *ranking* (values in results are
  already masked by the read advice — the ranking was the inference oracle). Rows re-embed on
  their next write (masked source → stays NULL; unmasked → re-embeds). External adapters no-op.
Also: between storage-adapter decryption and the advice, plaintext exists in-process —
never log field values on that path. **`field_history` is now live** (writer:
`FieldHistoryHook`, wildcard, captures create/update/delete diffs of `trackHistory` fields;
read: `/api/field-history` system collection + gateway route). Its masked/hidden-field leak
risk is closed by `FieldHistorySecurityAdvice`, which resolves each row's *referenced*
collection+field and drops FLS-denied rows / redacts MASKED old+new values per requester
(the generic `CerbosFieldSecurityAdvice` can't, since it keys off the row's own
`field-history` type). Row-drop makes a page's returned count a lower bound — acceptable for
an audit feed.

## Known Bugs

(No open bugs from the original audit. See Resolved → Bugs.)

**FIXED (PR #1174) — record-policy `collectionId` was UUID-keyed but checked by name.**
`CerbosPolicyGenerator.generateRecordPolicy` emitted `R.attr.collectionId == "<UUID>"` (from
`profile_object_permission.collection_id` + `collection.id`, both UUIDs), but
`CerbosRecordAuthorizationAdvice` sets `R.attr.collectionId` from the URL path **name**. With
`permissions-enabled=true` (default) and real Cerbos policies, per-collection record allow rules
never matched, so any profile lacking `VIEW_ALL_DATA` (e.g. the seeded **Standard User**) had
**every record filtered out** of reads and denied on writes — except explicitly record-shared
rows. The harness runs Cerbos in allow-all mode and e2e runs as admin, so neither caught it (same
blind spot as the field-permission bug, per `feedback_db-constraint-test-gap`). Fix: the record
policy CEL (and its custom-rule CEL) now key on the collection **name** via a `collectionIdToName`
map; the **collection** policy stays UUID-keyed because the gateway's `checkObjectPermission`
passes the UUID (`route.getId()`). See `architecture.md` → Cerbos `collectionId` keying.
**Pre-merge gate: verify on a live stack** (`make up`, log in as a non-admin profile, toggle
per-collection object permissions) — the allow-all harness Cerbos cannot exercise real deny.

## Tech Debt

**DB-design audit — incomplete legacy removals (2026-07-06).** A schema audit (159 migrations
cross-referenced against the live `192.168.0.5/emf_control_plane` DB: 117 public tables, 55 empty)
found dead references to tables dropped years earlier when the platform moved to profiles + Cerbos.
Staged cleanup:
- **PR1 (shipped #1186): permission sets fully removed** — the six V98-dropped permission-set tables
  were still registered as system collections and queried by delegated admin. Migration V160 dropped
  the `delegated_admin_scope.assignable_permission_set_ids` column.
- **PR2 (this change, #1187): package export/import + config-health.** `PackageService`/`PackageRepository`/
  `PackageImportService` were still exporting/importing `role`/`policy`/`route_policy`/`field_policy`
  (dropped V47) — package export threw on those item types; `OverpermissiveProfileRule` selected `FROM
  system_permission` (dropped V47) — the config-health scan failed. Removed the authz export/import
  path (methods, repo queries, natural-key remap machinery) and fixed the rule to read
  `profile_system_permission`.
- **PR3 (shipped #1188): orphan tables + dead endpoint.** Dropped `user_group_member` (V12 flat join,
  superseded by `group_membership` V45; the one stale row is discarded legacy data) and
  `flow_execution_dedup` (V71, unused) via `V161__drop_orphan_tables.sql`. Deleted
  `WorkflowMigrationController`/`WorkflowMigrationRepository` — they queried `workflow_rule`/
  `workflow_action` (dropped V72), so the endpoint was dead-broken, not "harmless" as previously noted.
- **PR4 (pending): Flyway history flatten** to a clean V1 baseline; rehearse the no-op on a
  `192.168.0.5` clone before rollout.

**Feature-flag audit outcomes (2026-07-02).** The audit reviewed every `@ConditionalOnProperty`
for removal ("inline the current value"). Findings:
- **Removed**: frontend `RENDER_TREE_V2` (was hardcoded `true`; legacy per-type renderer deleted — #1137).
- **RETAINED — not dead, do not inline**: `kelta.scheduler.enabled` + `kelta.bulk.processor.enabled`
  are set to `false` by `application-migrate.yml` so the K8s PreSync **migration pod** starts no
  background workers and exits after runners complete. Inlining them would hang the migrate job.
  `kelta.email.enabled=false` provides a no-op `EmailService` for envs without SMTP (dev/test).
  `kelta.flow.enabled` / `kelta.modules.runtime.enabled` likewise gate real subsystems.
- **RETAINED — presence-gated secrets/integrations** (removing the gate constructs a bean with a
  null secret → startup failure): `kelta.encryption.key`, `kelta.svix.auth-token`,
  `kelta.superset.url`, `kelta.sms.provider`, `kelta.push.provider`.
- **Security flags** `kelta.{worker,gateway,auth}.internal-auth.enabled` (default off): flipping ON
  + removing is a prod behavior + security change requiring the gateway client-credentials path
  verified first — a scoped follow-up, not a mechanical inline.

**Deliberately-retained code (audit judged deletion riskier than the cleanliness gain):**
- Email-template dual lookup (`findTemplateByKey` vs `findTemplateByName`, see below) — both axes
  are live on the transactional-email path (password reset/invite); consolidating risks that path
  for a cosmetic gain.

**Large files needing decomposition:**

| File | Lines | Proposed fix |
|------|------:|--------------|
| `SystemCollectionDefinitions.java` | 1,434 | Move to JSON/YAML config loaded at startup |
| `DynamicCollectionRouter.java` | 1,357 | Extract RouteResolutionHandler, InclusionHandler, FieldMapperHandler |
| `PhysicalTableStorageAdapter.java` | 1,091 | Extract SQL builder classes; separate concerns |

**Other:**

- **Related-list mass-edit ships via bulk-jobs (2026-07-04; formerly deferred).** `RelatedList` row selection + "Edit field…" submits ONE `POST /api/bulk-jobs` UPDATE job and polls it — no N-sequential-PATCH loop. Two boundaries to keep in mind: (1) the affordance is gated on `MANAGE_DATA` (`useSystemPermissions`) because bulk writes bypass per-record Cerbos advice — the in-controller `MANAGE_DATA` gate is the server boundary; (2) bulk UPDATE runs through `QueryEngine.update`, so validation/hooks fire but field-level write-FLS advice does not (same accepted trade-off as record merge). Deleting a `master_detail` child is a normal delete; deleting the parent cascades server-side (FK CASCADE).
- `PageBuilderPage.tsx` (`kelta-ui/app/src/pages/PageBuilderPage/`) is still large (~1.3k lines) but net-shrank in slices 2a/2b/2c: the per-type render switches, `AVAILABLE_COMPONENTS`/`ComponentPalette`, the `PropertyPanel` blocks, and (2c) the in-file native-HTML5-DnD `Canvas` + drag handlers were extracted to `widgets/*`, `palette/Palette.tsx`, `inspector/*`, and `canvas/*`. Remaining bulk is the page-list table, the page-config form modal, and the editor shell — candidates for extraction if it grows further; not currently blocking.
- **`@dnd-kit/{core,sortable,utilities}` (slice 2c)** is a new `kelta-ui/app` runtime dep, **scoped to the page canvas only** — `PageLayoutsPage`/`MenuBuilderPage`/`FlowDesignerPage` stay on native HTML5 DnD; do NOT mix the two libs in one tree. Watch items: ~30–40 kB gz bundle cost; pointer-DnD is flaky in jsdom (interaction tests drive dnd-kit via the **KeyboardSensor** and assert the resulting tree, not pixels — pointer fidelity is covered post-deploy by Playwright). The `vitest.setup.ts` `ResizeObserver` mock is now a real class because `DndContext` does `new ResizeObserver(...)`.
- **Page save-path silent-drop class (slice 2c — closed for `schemaVersion`).** `mergeConfig` only overlays a key when the `handleSavePage` CALL passes it; widening `mergeConfig`'s accepted keys alone silently drops them. 2c passes the full set `{ components, variables, dataSources, schemaVersion: 2 }`. The invariant — *every page-level sibling must be passed at the call site* — must hold for every future sibling (2d adds `variables`/`dataSources` values, 1h adds `access`). A test asserts the `updateMutation.mutate` payload carries `schemaVersion`.
- **`config.layout` is inert/deprecated legacy (slice 2c).** The create-form `layoutType` select (`page-layout-select`) still writes `config.layout.type` and it round-trips untouched, but the widget tree + per-child `span` now own layout — nothing in the canvas or runtime reads `config.layout`. Removal of the select is deferred (it is also the create-time form field; ripping it out touches the create flow + tests). A future slice may delete it once no page reads `config.layout`.
- Hardcoded `emf_control_plane` DB name in `SupersetDatabaseUserService.java` (line 78) — env-driven via `kelta.worker.superset.database-name` default; cleanup is cosmetic.
- Potential N+1 in `SearchIndexService.java` during reindex (per-collection queries) — harmless under normal load; matters at >1k collections.
- **Email templates have two parallel lookup axes** — `EmailRepository.findTemplateByKey` resolves by the stable `template_key` column (V133 seeded eight `user.*` defaults); `findTemplateByName` resolves by the human-friendly `name` column (V141 seeded `password_reset`, `user_invite`, `welcome`). Both implement the same tenant-override → `'system'`-sentinel fallback, so callers picking the "wrong" axis still work but may land on a different seed row than intended. Pick one canonical axis once the calling code settles, and drop the unused seed set.

## Fragile Areas

- **Destructive schema-migration execute** (`MigrationExecutionService` + `SchemaMigrationEngine.migrateSchemaDestructive`): the only path that physically `DROP COLUMN`s live tenant data. It applies DDL, then re-registers the local `CollectionRegistry` with the target def and broadcasts `kelta.config.collection.changed`. On the originating pod this is exact (direct `register(target)`, no re-migrate). **Other pods** react to the broadcast via `refreshCollection`, which reloads the target from the `field` table (correct) but *also* calls `updateCollectionSchema` → the non-destructive `migrateSchema`, which tries to **deprecate** the just-removed column via `COMMENT ON COLUMN`. Because the column is already dropped in the shared DB, that COMMENT fails — but `refreshCollection` swallows the error after it has already re-registered the target def, so all pods still converge. Net effect: correct state everywhere, with a harmless one-off `StorageException` logged on non-originating pods per removed field. Tightening this (make deprecate tolerant of a missing column, or route the drop through the refresh path) is a follow-up. Gated on `CUSTOMIZE_APPLICATION`; **security-sensitive → not auto-merged**.
- **FK constraint generation** (`PhysicalTableStorageAdapter.java` 177-185): String concatenation for FK names; collision risk with very long tenant slugs + collection names.
- **Tenant schema isolation** (`PhysicalTableStorageAdapter.java` 105-107): Assumes schema exists; silent failure on permission error. Surfaces as confused-state on tenant provisioning failure.

## Dependency Risks

- **Gateway is a GraalVM native image — every DTO deserialized from `/internal/bootstrap` MUST be
  in `reflect-config.json`.** Root cause of the 2026-07-02 total-API outage: V148 added
  `ipAllowlists: Map<String,TenantIpConfig>` to `kelta-gateway/.../config/BootstrapConfig` + the
  `TenantIpConfig` DTO, but nobody added `TenantIpConfig` to
  `kelta-gateway/src/main/resources/META-INF/native-image/io.kelta/kelta-gateway/reflect-config.json`.
  In the JVM this is invisible (reflection is free); in the native image Jackson can't construct the
  class → the whole bootstrap fails to deserialize → the gateway loads ~2 routes instead of ~148 →
  every `/{tenant}/api/*` 404s (`No static resource`) → app + login down. It stayed latent until the
  first gateway rebuild after V148. **Rule: any class reachable from `BootstrapConfig` (or any
  reflectively-deserialized gateway type) gets a `reflect-config.json` entry in the same PR.** The
  route loader is now hardened (`RouteConfigService.refreshRoutes` registers the static routes
  *before/independent of* the bootstrap fetch), so a future missed DTO degrades (loses only dynamic
  collection routes) instead of taking the entire API offline — but the reflect-config entry is still
  required for the bootstrap to fully load.
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

- Schema migration edge cases (`SchemaMigrationEngine` — ~880 lines) — the destructive `migrateSchemaDestructive` (ADD/DROP/ALTER) path now has engine unit tests + a real-DB `SchemaMigrationScenarioTest`; the older non-destructive `migrateSchema` (deprecate) branch is still lightly covered.
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
