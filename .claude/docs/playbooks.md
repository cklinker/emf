# Playbooks — end-to-end recipes

Step-by-step file-lists for the common tasks. Each recipe is the **exact set of files to
create/edit, in order**, plus the non-obvious registration steps that an agent would
otherwise discover only by reading source. Every class/path here is verified against the
codebase — but verify it still exists before relying on it (code wins over docs).

Before starting any recipe: check [`status.md`](status.md) (is the subsystem real / already
built?) and [`concerns.md`](concerns.md) (is a file you'll touch fragile?).

Index: [Flow action handler](#1-add-a-flow-action-handler) ·
[Worker REST endpoint](#2-add-a-worker-rest-endpoint) ·
[Admin UI page](#3-add-an-admin-ui-page) ·
[MCP tool](#4-add-an-mcp-tool) ·
[System collection](#5-add-a-system-collection) ·
[Field type](#6-add-a-field-type)

---

## 1. Add a flow action handler

A handler that runs as a Task state in the Visual Flow Builder (e.g. `SendSlackMessage`).

**Backend**
1. Create `kelta-platform/runtime/runtime-module-integration/src/main/java/io/kelta/runtime/module/integration/handlers/<Name>ActionHandler.java` implementing `io.kelta.runtime.workflow.ActionHandler`:
   - `String getActionTypeKey()` → the action type (e.g. `"SEND_SLACK_MESSAGE"`).
   - `ActionResult execute(ActionContext context)` → read **static node config** from `context.actionConfigJson()` (parse with `ObjectMapper`) and **runtime-resolved values** from `context.resolvedData()` (the `$.input.*` envelope feeds this); do the work; return `ActionResult.success(map)` / `failure(...)`.
   - optional `validate(...)` and `getDescriptor()`.
   - Collaborators are **constructed** (not field-injected) and pulled from `ModuleContext.getExtension(Type.class)` in the module. ⚠️ `RestTemplate` is **not** a registered extension — default it (`new RestTemplate()`) if null. Resolve secret refs (webhook URLs/keys) via the `CredentialResolverPort` extension. See `runtime-module-integration/CLAUDE.md` → Handler collaborators & config.
2. **Register it** (the step no agent guesses): in `runtime-module-integration/.../IntegrationModule.java`, construct your handler in `onStartup(ModuleContext)` and return it from `getActionHandlers()`. The module is wired as a `@Bean` in `kelta-worker/.../config/FlowConfig.java` (`integrationModule()`); `ModuleRegistry` calls `onStartup` then registers handlers into `ActionHandlerRegistry`.
   - ⚠️ Handlers are **NOT** `@Component`-scanned. The `ActionHandler` Javadoc that says "implement as a `@Component`" is **wrong** — a `@Component` handler silently never registers.
   - ⚠️ The `workflow_action_type` DB table is a **display catalog only**; its `handler_class` column is never read by Java. You do **not** need a migration for the handler to execute.
   - Reference impl: `handlers/HttpCalloutActionHandler.java`. (Pure data/CRUD handlers go in `runtime-module-core` via `CoreActionsModule` instead.)

**Frontend — make it usable in the builder** (the flow builder is hardcoded TS, not descriptor-driven):
3. Add the action to `RESOURCE_GROUPS` in `kelta-ui/app/src/pages/FlowDesignerPage/types.ts`.
4. Write a `<Name>Params.tsx` config form in `kelta-ui/app/src/pages/FlowDesignerPage/components/properties/` (props: `{ parameters, onUpdate }`; ref `HttpCalloutParams.tsx`) and wire it into `TaskProperties.tsx` (switched on `resource`).
   - ⚠️ The `ActionHandlerDescriptor` SPI exists but the admin UI does **not** consume it for built-in handlers — the palette + params are hand-written. Skipping this step ships a handler that never appears in the builder.

**Tests**: `<Name>ActionHandlerTest.java` co-located (JUnit 5 + Mockito, MockWebServer for outbound HTTP) + a Playwright e2e exercising the node.
**Docs**: bump the handler count + list in [`status.md`](status.md) (Visual Flow Builder row); [`integrations.md`](integrations.md) if it's a new external dependency.

---

## 2. Add a worker REST endpoint

A new `GET/POST /api/...` served by the worker, tenant-scoped, optionally permission-gated.

**Files**
1. Controller in `kelta-worker/.../controller/` — thin, delegates to a service. (Ref: `ModuleController.java`; test ref: `scim/controller/ScimUserControllerTest.java`.)
2. Service in `kelta-worker/.../service/`.
3. Repository in `kelta-worker/.../repository/` — `@Repository` using `JdbcTemplate` + raw SQL (**not** JPA; models are records). Ref: `repository/ApprovalRepository.java`.

**Tenant scoping** (don't reinvent it):
- Tenant context is **already bound** per request by `kelta-worker/.../filter/TenantContextFilter.java` from the gateway's `X-Tenant-ID` / `X-Tenant-Slug` headers. In the controller, **read** the current tenant from `TenantContext` (or accept `@RequestHeader("X-Tenant-ID")`). Do **not** call `TenantContext.runWithTenant(...)` on a request path — that's for background/cross-tenant jobs. Postgres RLS then scopes every query automatically.

**Routing & authorization** (the part docs used to get backwards):
- If your path is under an **existing** top-level segment (e.g. `/api/admin/...`), the gateway already routes it.
- If it introduces a **new** top-level segment (`/api/<x>/**`), add a static-route row in `kelta-gateway/.../service/RouteConfigService.registerStaticRoutes()` (and `config/RouteInitializer.registerStaticRoutes()`) with id `static-<x>` — otherwise the gateway returns **404** before reaching the worker.
- **Authorization reality**: `static-*` routes (`/api/admin/**`, `/api/me/**`, etc.) are **skipped** by the gateway's collection-level Cerbos check (`RouteAuthorizationFilter` skips ids starting `static-`) — they get only the blanket `API_ACCESS` system-permission check. The worker's `CerbosRecordAuthorizationAdvice` and FLS advices **exclude** `/api/admin/`. So a new admin endpoint is, by default, reachable by **any** authenticated user with API access. To enforce a *specific* permission, **check it inside the controller/service**: inject `CerbosPermissionResolver` for identity (`getProfileId`/`getEmail`/`getTenantId` — the gateway forwards `X-User-Profile-Id`/`X-User-Email`/`X-Cerbos-Scope` on every request incl. admin) and check `profile_system_permission` like `SupersetGuestTokenService.hasSystemPermission(profileId, name)`. Full detail: `architecture.md` → Authorizing a new endpoint. Don't assume "it's under /api/admin so it's protected."

**Response contract**: build success with `JsonApiResponseBuilder` (`runtime-jsonapi`); errors flow through the runtime-core router `GlobalExceptionHandler` into the JSON:API error envelope (`code` = `UPPER_SNAKE_CASE`) — see [`conventions.md`](conventions.md) (canonical). Pagination: `page[number]`/`page[size]`, default 20, clamp `MAX_HTTP_PAGE_SIZE = 200` via `Pagination.fromParams`.

**Tests**: controller test (mirror `ScimUserControllerTest`) + service/repository tests.
**Docs**: [`architecture.md`](architecture.md) if it adds a data flow; "Keeping Docs Current" if a new top-level path (gateway static route).

---

## 3. Add an admin UI page

**Files & wiring**
1. Page component in `kelta-ui/app/src/pages/<Name>/`.
2. **Register the route** (no central router config beyond this): in `kelta-ui/app/src/App.tsx`, add a `React.lazy(...)` import and a `<Route>` inside the tenant routes, wrapping the element in `<AdminPageRoute requiredPolicies={[...]}>` (applies `ProtectedRoute` + policy guards) and/or `<RequirePermission permission="VIEW_SETUP">`. ⚠️ Different prop shapes: `AdminPageRoute` takes `requiredPolicies?: string[]`, `RequirePermission` takes `permission: string`.
3. **Nav/menu**: there is **no sidebar-config array** and `Header` has no nav list. Surface the page by adding an entry to the `defaultCommands` array in `kelta-ui/app/src/components/SearchModal/SearchModal.tsx` — shape `{ id, type: 'page', title, subtitle, path }` (the `Cmd/Ctrl+K` palette).

**Data fetching**
- Collection data: use the existing TanStack Query hooks (`useCollectionRecords`, etc.).
- **Non-collection / admin endpoints**: there is no generated hook — call `apiClient.get('/api/admin/...')` (`kelta-ui/app/src/services/apiClient.ts`) inside a `useQuery`. `apiClient` only auto-unwraps standard `/api/{collection}` JSON:API; admin controllers often return a `single(...)` envelope with rows under `attributes` — unwrap manually.

**Styling**: follow `kelta-ui/DESIGN.md` (uppercase-11px field labels, token colors not hex, Lucide icons, em-dash empty states). **Reuse `@kelta/components`** — never fork a new table/filter/form (see [`conventions.md`](conventions.md)).
**Tests**: Vitest + Testing Library + MSW (unit) and a Playwright e2e in `e2e-tests/`.
**Docs**: [`kelta-ui/README.md`](../../kelta-ui/README.md) routes table; [`status.md`](status.md) if it's a new capability.

---

## 4. Add an MCP tool

Expose a platform operation to MCP clients (kelta-admin or kelta-user toolset).

**Files**
1. Tool class in `kelta-mcp/.../tool/admin/` (admin) or `tool/user/` (data). Implement the marker interface **`AdminTool`** or **`UserTool`** as a `@Component`:
   - `toSpecification()` returns `io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification`.
   - Build the input schema with `Schemas`; annotate behavior with `ToolHints`.
   - In the call handler, translate **friendly camelCase args → native JSON:API body** at the tool boundary (see [`conventions.md`](conventions.md) → MCP tools), then forward via `GatewayHttpClient` (`http://emf-gateway:80`); shape the JSON:API response into MCP content; map gateway 4xx/5xx via `McpErrorMapper` (redacts `klt_`).
2. **Registration is automatic**: Spring autowires `List<AdminTool>` / `List<UserTool>` and `kelta-mcp/.../config/McpServerConfig.java` registers them. Do **NOT** hand-write `server.addTool(...)`.
   - ⚠️ The old "`server.addTool(new Tool().toSpecification())` + `McpServerFeatures`" pattern is wrong — it won't compile/register.

**Endpoint map**: e.g. `create_validation_rule` → `POST /api/validationRules` (type `validationRules`); pass `collectionName` through verbatim (worker resolves it) — don't pre-resolve names unless the existing tools do.
**Tests**: `<Name>ToolTest.java` asserting the on-the-wire body with WireMock JSON-path matchers (every sibling tool has one).

---

## 5. Add a system collection

Platform-managed metadata (not a user collection) — e.g. `feature_announcements`.

1. **Declare it**: in `runtime-core/.../model/system/SystemCollectionDefinitions.java`, add a `<name>()` factory using the private `systemBuilder(name, displayName, tableName)` helper (it injects audit fields + `systemCollection(true)` + the physical table), then add `definitions.add(<name>())` to `all()`.
2. **Create the table** (the worker does **NOT** auto-create system tables — Flyway owns them):
   - Ship `kelta-worker/src/main/resources/db/migration/V<next>__create_<table>.sql` (check the dir for the head number; currently V142 → V143).
   - Add the table to the RLS-enable migration set (the V77-style migration hardcodes table names) so RLS applies.
3. **Multi-pod refresh**: register a `<Name>RefreshHook` as a `@Bean` in `kelta-worker/.../config/FlowConfig.java` via `hookRegistry.register(hook)`. The hook implements `BeforeSaveHook`, and in `afterCreate/Update/Delete` publishes via `PlatformEventPublisher` to `kelta.config.collection.changed.<id>` (reuse `CollectionConfigEventPublisher.SUBJECT_PREFIX`, build a `CollectionChangedPayload` via `EventFactory`). All pods consume it via `NatsSubscriptionConfig` → `CollectionLifecycleManager` refreshes the `CollectionRegistry`. See [Critical Rule 1](../../CLAUDE.md).
   - Reference impl: `listener/ValidationRuleRefreshHook.java`, `listener/CollectionConfigEventPublisher.java`.
   - ⚠️ The reference `ValidationRuleRefreshHook.afterDelete` is a **no-op** (the `collectionId` isn't available on delete). If your collection needs delete to refresh other pods, handle it explicitly rather than copying the no-op.
**Tests**: registry/refresh unit test + migration applied against a non-empty DB.
**Docs**: [`status.md`](status.md); [`concerns.md`](concerns.md) notes `SystemCollectionDefinitions.java` is already 1,400+ lines — keep additions minimal.

---

## 6. Add a field type

1. Add the constant to the `FieldType` enum (`runtime-core/.../model/FieldType.java`).
2. Map it to a Postgres column in the storage layer (`PhysicalTableStorageAdapter` / `SchemaMigrationEngine`) — pick the SQL type, nullability, and any companion column (e.g. currency has a currency-code column).
3. Add validation handling if the type has constraints (`runtime-core/.../validation/`).
4. Frontend: add a renderer/editor in the `@kelta/components` `FieldRenderer` family (reuse, don't fork) and any admin field-config UI.
5. MCP: add the friendly alias → enum mapping in `FieldBodyBuilder.resolveNativeType` (kelta-mcp) so `add_field`/`create_collection` accept it.
**Tests**: storage round-trip + validation unit tests; component test for the renderer.
**Docs**: [`status.md`](status.md) (Dynamic schema engine row).

---

These recipes assume the conventions in [`conventions.md`](conventions.md) and the rules in
[`../../CLAUDE.md`](../../CLAUDE.md). When you complete one, update the docs it names — that's
how the next agent gets a correct recipe.
