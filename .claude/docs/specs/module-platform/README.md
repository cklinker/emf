# Module Platform — Runtime Add-Ons as a First-Class Extension Surface (Parent Spec)

> **Status:** parent planning spec. Authoritative shared contract for turning runtime-installable
> modules from "a JAR that can register flow handlers" into an add-on surface a third party can
> build a real product on: declare what you contribute, declare what you need, get admin approval,
> fail loudly, and be upgradable and removable without orphaning a tenant's data.
>
> The forcing function is the **complete removal of compiled-in billing** from the platform
> (slice 8). Billing already runs as a runtime module on the `<tenant-slug>` prod tenant — a
> compiled-in Spring controller resolving a member's entitlements out of a signed, uploaded JAR —
> so the mechanism is proven. What is not proven is everything around it.
>
> Source-verified against the codebase on 2026-08-31 (Flyway directory head **V188** — next new
> migration is **V189**; deployed `flyway_schema_history` keeps pre-flatten numbering, so always
> check the directory *and* deployed history before numbering). If code and this doc disagree,
> trust the code and fix this doc.

## How to use this document

This parent defines the cross-cutting architecture once. Child specs each cover one PR-sized slice
with acceptance criteria, exact contracts, DB migrations (or "none"), file-by-file changes, and a
test plan — per the child-spec template in `specs/app-surfacing/README.md` (sections 1–8; sections
that don't apply state "N/A — reason"). Read this parent first; every child references it.

---

## Security model — read this before designing anything

`SandboxedModuleClassLoader.ALLOWED_PARENT_PREFIXES` includes **`java.` and `javax.` wholesale**. A
loaded module can open sockets, read the filesystem, spawn threads, and call `System.exit` — inside
the worker JVM, with the worker's database connection. On the browser side, `moduleUiBundles.ts`
evaluates publisher JavaScript from a blob URL, same-origin, with the admin session's full DOM and
cookie access.

**The JAR publisher signature is the entire trust boundary.**

Everything this spec calls a *capability* is **disclosure and coarse gating at platform chokepoints
— not containment**. A capability tells an admin what a module intends to reach and lets the
platform refuse at a few specific call sites. It does not confine the module.

This must stay stated plainly. The risk of leaving it implicit is that `capabilities: [...]` in a
manifest reads like a sandbox to a future maintainer, who then relaxes the signature gate on that
assumption. `RouteAuthorizationFilter` already gets this right: designating a module signing key is
gated on `MANAGE_CREDENTIALS` precisely because it is a grant of code-execution authority.

Corollaries that follow from this and are **not** open questions:

- Per-module resource quotas, thread isolation, and `SecurityManager`-style sandboxing are not
  buildable on a supported JVM mechanism. Do not design as if they exist.
- `data:read` / `data:write` capabilities will not be shipped. A module holds `QueryEngine`
  directly; the platform would not be enforcing them, and a capability the platform does not
  enforce is worse than no capability at all.

---

## Reuse map — what already exists

The single most important finding behind this spec: **the platform already has a metadata
provisioning engine, and modules are not using it.**

| Need | Existing thing to reuse | Location |
|---|---|---|
| Provision collections, fields, picklists, validation rules, layouts, flows, UI pages, menus | `PackageImportService` — upserts **12 types by natural key**, SKIP/OVERWRITE, dry-run, per-item report, routed through `QueryEngine` so NATS broadcast + table DDL fire normally | `kelta-worker/.../service/PackageImportService.java` (582 lines) |
| Module HTTP surface | `/api/modules/**` is **already a static gateway route**; `RouteAuthorizationFilter` enforces `API_ACCESS` on static routes and leaves finer checks to the worker | `kelta-gateway/.../service/RouteConfigService.java:180` |
| Unauthenticated inbound | Generic platform-owned webhook route + tenant binding | `kelta-worker/.../controller/ModuleWebhookController.java` |
| Permission enforcement | Cerbos policies are **generated from `profile_system_permission` rows** and pushed at runtime — one Cerbos action per distinct permission name. **No Cerbos change is needed to add a permission** | `CerbosPolicyGenerator`, `CerbosPolicySyncService` |
| Secrets | The credential vault: encryption, rotation, `MANAGE_CREDENTIALS` gating, and resolve auditing (V188) | `CredentialResolverPort` via `ModuleContext.getExtension(...)` |
| Periodic work | `scheduled_jobs` rows of type `FLOW` → a flow whose Task calls a module action key | `ScheduledJobExecutorService` |
| Module→platform services | `ModuleServiceRegistry`, tenant-scoped, identity-based removal | `runtime-core/.../module/service/ModuleServiceRegistry.java` |
| i18n | `ui-translations` rows, overlaid client-side over bundled strings | `SystemCollectionDefinitions`, `bootstrapCache.ts` |

`ModuleCollectionProvisioner` is a weaker re-implementation of two of `PackageImportService`'s
twelve types. Slice 2 retires it.

---

## What is closed, and stays closed

| Extension point | State | Decision |
|---|---|---|
| Credential types | `CredentialTypeRegistry` built once from Spring `List<CredentialType>`; no `register()` | **Not opening.** A module *uses* credentials via `credential-ref` settings. A module needing a typed credential leaves a compiled-in residue — a recorded platform limitation, not per-feature debt |
| `FieldType` | Java enum carrying Postgres column-type mappings that drive DDL | **Not opening.** Compose existing types; `JSON` covers structured cases |
| MCP tools | `kelta-mcp` is a separate Spring service — no module classloader, stateless transport, `List<AdminTool>` fixed at construction | **Not opening.** If needed, generate tools from `tenant_module_action` rows over HTTP; never load module code into kelta-mcp |
| NATS streams / subscriptions | `JetStreamInitializer` + `NatsSubscriptionConfig` are startup-bound | **Not opening.** `kelta.trigger.<tenantId>.<topic>` is already an open runtime subject space |
| Arbitrary gateway paths | `RouteConfigService.registerStaticRoutes()` is a hardcoded array | **Not opening.** The `/api/modules/{moduleId}/...` prefix gives the same capability with no collision surface |

---

## Shared contracts

### Module status

`INSTALLED` · `ACTIVE` · `DEGRADED` · `QUARANTINED` · `DISABLED` · `FAILED` · `STUB`

- `DEGRADED` — loaded, but something declared is unavailable (required setting unset, declared port
  refused, UI bundle missing). Real handlers for what loaded; quarantined for the rest.
- `QUARANTINED` — load failed (signature, checksum, classload, `onStartup` threw). Quarantined
  handlers only.
- `STUB` — **explicit dev opt-in** (`kelta.modules.stub-mode=true`), never reached by falling back
  from an error, and reported as such by the API and UI.

**The DB status column is not pod reality.** `isLoaded()` is per-pod; `GET /api/modules/{id}/health`
reports both and names the pod.

### Quarantined handler

A module whose load failed registers handlers that return
`ActionResult.failure("ModuleUnavailable", "<moduleId> v<version> is quarantined: <reason>")` —
registered rather than absent, so a flow step fails with a specific attributable error instead of
`ResourceNotFound`, which reads to an admin as a mistyped action key.

### Provenance

Every resource a module provisions is recorded in `module_provisioned_resource` with:

- `ownership` — `CREATED` (the module made it) vs `ADOPTED` (it already existed and the module
  reused it). **Uninstall may only ever remove `CREATED`.**
- `content_hash` — the content at provisioning time. A differing hash means the tenant has edited
  it since, so uninstall leaves it and reports it. This is what stops uninstall silently reverting
  an admin's work.

### Tenant binding

Any platform code that calls into a module **must** bind `TenantContext` with **id and slug**. A
module cannot resolve a slug (`TenantSlugResolver` is platform-side), and a null slug does not fail
— the query engine reads the *public* schema instead of the tenant's, answering from the wrong data.
This is the exact defect fixed in #1390; it is a contract, not a preference.

### Idempotent / destructive / consent

- **Idempotent:** JAR upload (version-keyed), package import (natural-key upsert), permission
  upsert, provenance upsert, route/schedule registration, load/unload.
- **Destructive, never automatic:** dropping collections or fields, deleting records, deleting a
  tenant-modified metadata item, revoking a permission grant an admin made.
- **Explicit admin consent:** install (capability + permission approval via `planHash`), downgrade,
  `purgeData`, `OVERWRITE` on tenant-modified metadata, an upgrade whose plan removes an action key
  a live flow references.

---

## Slice plan

| Slice | Child spec | Axis |
|---|---|---|
| 0 — Fail closed | `0-fail-closed.md` | **backend, security** — kill the silent stub fallback |
| 1 — Billable-channel guard | `1-billable-channel-guard.md` | backend (money risk, ~20 lines) |
| 2 — Provenance + unified provisioning | `2-provenance-provisioning.md` | backend (DB, reuse `PackageImportService`) |
| 3 — Lifecycle | `3-lifecycle.md` | backend (manifest v2, install plan, upgrade, uninstall) |
| 4 — Permission catalog | `4-permission-catalog.md` | **backend + UI, security** |
| 5 — Module settings | `5-module-settings.md` | backend + UI (credential-ref) |
| 6 — Module HTTP routes | `6-module-routes.md` | **backend, security** |
| 7 — Capability gating | `7-capability-gating.md` | **security** (closes an open hole) |
| 8 — Billing removal | `8-billing-removal.md` | **the proving slice** — 6 PRs, ends with a Flyway drop |

Slice 8 exercises every earlier slice: routes (checkout/portal), webhooks (Stripe), settings
(`credential-ref` to the Stripe key), permissions (`MANAGE_BILLING`), provisioning (five
collections), schedules (pass expiry), a published service port (`EntitlementProvider`), and
fail-closed (billing must **never** stub-succeed).

---

## Known defects this spec exists to fix

Each is source-verified as of 2026-08-31.

1. **The stub fallback reports success.** `RuntimeModuleManager.loadFromJar` catches every
   exception and calls `loadWithStubs`; `createStubHandler` returns
   `ActionResult.success({"status":"EXECUTED","mode":"stub"})` while the module reports `ACTIVE`.
   A cryptographically rejected module is indistinguishable from a working one, and flows calling
   it pass.
2. **The admin UI cannot install a signed JAR.** `ModulesPage` only calls
   `/api/modules/install` (manifest JSON), never `/install-jar` — there is no file upload or
   signature field. Every UI-installed module is stub-mode by construction.
3. **`manifest.permissions` and `minPlatformVersion` are dead.** Parsed, stored, and read by zero
   non-test code. A module author would reasonably believe both work.
4. **`getServices()` is ungated.** `registerTenantServices` registers whatever the module returns.
   Any installed module can publish `EntitlementProvider` and silently become that tenant's
   entitlement authority. Needs a signed JAR and an admin install, so not remotely exploitable —
   but a module should only publish ports it declared and an admin approved.
5. **No upgrade path.** v2 over v1 throws 409, and in `installModuleWithJar` the S3 upload happens
   *before* the duplicate check, orphaning a JAR that nothing ever deletes.
6. **Uninstall leaves data with no provenance.** Nothing records which collection came from which
   module, so an admin cannot find the orphans afterwards.
7. **No module settings** anywhere — manifest, DB, SPI, API, or UI.
8. **No observability.** No metrics, no health, no per-module error attribution; `isLoaded()` exists
   and no endpoint returns it; `FAILED` is written in exactly one place and is nearly unreachable
   because the stub fallback swallows the exception first.
9. **Cross-pod divergence is silent.** Install/enable rely on a fire-and-forget NATS event with no
   ack; a pod that misses one diverges while the API reports `ACTIVE`.

---

## Deploy hazards that apply to every slice

1. **The worker must stay JVM.** `build-and-publish-containers.yml` pins `Dockerfile.jvm` because a
   native image cannot classload an uploaded JAR — a native worker turns **every** module into
   stubs. Before slice 8d that is degraded-but-covered; after it, it silently ungates every tenant.
   Slice 0 adds a CI assertion that the worker build references `Dockerfile.jvm`.
2. **The merge-train trap.** The deploy workflow sets `cancel-in-progress: true` and filters paths
   against the previous push, so merging a second PR before the first's build completes can leave
   merged code unbuilt with green CI. In slice 8 that means a migration deploying without its code,
   or the reverse. **Merge one, wait for green, verify the ArgoCD tag moved.**
3. **The migrate Job is an ArgoCD PreSync hook** — it runs to completion while the *old* pods still
   serve traffic. A drop migration therefore removes tables underneath running readers, which is
   why 8f ships at least one release after 8e has soaked.
4. **`kelta-modules/**` is not a deploy trigger.** Every module-side change is a manual build, sign,
   install, verify, and appears in no deploy.
5. **`install-jar` activates immediately.** Handlers, hooks and services register at *install*;
   `status` stays `INSTALLED` until `/enable` — that flag is bookkeeping, not a gate. On a tenant
   with existing data, populate collections first.

---

## Docs to update (per CLAUDE.md Rule 6)

- `.claude/docs/status.md` — Extensibility/modules row per slice; the portal-billing row shrinks to
  nothing at slice 8.
- `.claude/docs/concerns.md` — close the silent-stub and ungated-`getServices()` items; add the
  entitlement-caching regression (slice 8d).
- `.claude/docs/playbooks.md` — "Ship a runtime-installable module" gains routes, settings,
  permissions, upgrade; the install-ordering note already lands there.
- `.claude/docs/architecture.md` — module HTTP dispatch; `/api/billing/**` row removed at 8b.
- `CLAUDE.md` — Module Map, the `specs/` reference row (this parent), messaging table at 8c.
- `kelta-modules/billing/README.md` + `VERIFICATION.md` — the module becomes the reference add-on.
