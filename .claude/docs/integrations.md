# External Integrations

## APIs & Services

| Service | Purpose | SDK/Version | Connection | Key Files |
|---------|---------|------------|------------|-----------|
| Anthropic Claude | AI assistant | `anthropic-java` 2.18.0 | API key | `kelta-ai/.../service/AnthropicService.java` |
| Cerbos | Authorization (gRPC) | `cerbos-sdk-java` 0.12.0 | `${CERBOS_HOST}:${CERBOS_GRPC_PORT:3593}` | `kelta-gateway/.../authz/cerbos/CerbosAuthorizationService.java`, `kelta-worker/.../service/CerbosAuthorizationService.java` |
| Svix | Webhook delivery | `com.svix:svix` 1.68.0 | `${SVIX_SERVER_URL}` | `kelta-worker/.../listener/SvixWebhookPublisher.java`, `.../service/SvixTenantService.java` |
| Superset | Embedded analytics | REST API | `${SUPERSET_URL}` | `kelta-worker/.../service/SupersetApiClient.java`, `kelta-ui/app/` (`@superset-ui/embedded-sdk`) |
| AWS S3 / Garage | Object storage | `aws-sdk-s3` 2.30.1 | `${KELTA_S3_ENDPOINT}` | `kelta-worker/.../service/S3StorageService.java` |
| Keycloak | OIDC federation | Spring Security OAuth2 | Port 8180 (docker-compose) | `kelta-auth/.../federation/FederatedUserMapper.java` |

### Attachment lifecycle (S3)

Files attach to any record via the `attachments` system collection (backed by `file_attachment`, `S3StorageService`). The full lifecycle:

1. **Upload** — `POST /api/attachments/upload-url` validates the file (type/size, per-tenant storage governor), creates a pending row, and returns a presigned S3 PUT URL. The client PUTs the bytes directly to S3, then `POST /api/attachments/{id}/finalize` verifies the object exists and records the storage key. (`AttachmentUploadController`)
2. **List / read / download** — `attachments` is routed by `DynamicCollectionRouter` like any collection (`GET /api/attachments?filter[collectionId][eq]=…&filter[recordId][eq]=…`, get-by-id, `?include=attachments`). `AttachmentUrlEnricher` (`@ControllerAdvice`, `@ConditionalOnBean(S3StorageService)`) injects a presigned `downloadUrl` for any `attachments` resource with a `storageKey`. `FileController` (`GET /api/files/**`) streams objects server-side with `Range` support.
3. **Delete** — `DELETE /api/attachments/{id}` (`AttachmentUploadController#deleteAttachment`) deletes the S3 object **and** the row. Its literal path beats the router's `/api/{collection}/{id}`, so the generic delete (which would orphan the S3 object) is bypassed. S3 deletion is best-effort and never blocks the metadata delete.
4. **Cascade cleanup** — `AttachmentCleanupHook` (wildcard `afterDelete`, order 200) removes a record's `file_attachment` rows + S3 objects when the **parent** record is deleted. Lookup uses `idx_attachment_tenant_record` (Flyway V142). The `attachments` collection itself is skipped (handled by the dedicated delete above). S3 cleanup is gated on `S3StorageService.isEnabled()`; row cleanup always runs.

## Data Storage

| Store | Purpose | Config Key | Details |
|-------|---------|-----------|---------|
| PostgreSQL 15 | Primary DB | `${SPRING_DATASOURCE_URL}` | Multi-tenant per-tenant schemas, Flyway migrations in `kelta-worker/.../db/migration/`. Dynamic user-collection tables are created by `PhysicalTableStorageAdapter.initializeCollection` which uses `CREATE TABLE IF NOT EXISTS` + `SchemaMigrationEngine.reconcileSchema` (introspects `information_schema.columns` and ALTERs to add missing ones) — this path must reconcile before issuing FK constraint statements so post-create `ADD CONSTRAINT FOREIGN KEY` references columns that exist. Because `CREATE TABLE IF NOT EXISTS` is **not atomic against concurrent CREATEs** in PG (two transactions can both pass the existence check and then both INSERT into `pg_type`, losing one with SQLSTATE 23505 on `pg_type_typname_nsp_index`), `initializeCollection` catches `DuplicateKeyException` from the CREATE step and falls through to `reconcileSchema`. This matters whenever the same NATS `CollectionChanged` event is delivered to multiple worker pods in parallel. CI: shared `kelta-ci-db` pool (schema-isolated per run, see `scripts/ci/README.md`); local: docker-compose or Testcontainers |
| OpenSearch 2.17.1 | Full-text search + audit | Port 9200 | `kelta-worker/.../service/OpenSearchQueryService.java`, `OpenSearchAuditService.java` |
| Redis 7 | Cache + sessions | `${REDIS_HOST}:${REDIS_PORT:6379}` | Route caching, permission caching, session management |
| Caffeine | Local in-memory cache | — | Hot-path caching alongside Redis |
| H2 | Test database | — | In-memory for unit tests |

## Messaging

**NATS 2.10** (`nats:2.10-alpine` with `--jetstream`)
- Server: `${NATS_URL:nats://localhost:4222}` (K8s: `nats.nats.svc.cluster.local:4222`)

| Subject | Purpose |
|---------|---------|
| `kelta.config.collection.changed` | Schema change events |
| `kelta.config.menu.changed.<tenantId>` | Nav menu/menu-item changed (apps/nav v2) — `MenuConfigEventPublisher` hooks on `ui-menus`+`ui-menu-items`; every worker pod evicts its `SystemCollectionCache` menu entries (`MenuCacheInvalidationListener`) |
| `kelta.config.tenant.ip-allowlist.changed.<tenantId>` | Tenant IP allowlist changed → gateway refreshes its allowlist cache |
| `kelta.config.environment.changed.<tenantId>.<envId>` | Sandbox environment lifecycle (created/cloned/refreshed/archived) |
| `kelta.config.promotion.executed.<tenantId>.<promotionId>` | Metadata promotion executed/failed/rolled back |
| `kelta.worker.assignment.changed` | Worker assignment changes |
| `kelta.record.changed` | Record CRUD events (`RecordChangedPayload`; carries `containsMaskedFields` when the collection has data-masking config — the gateway `RealtimeBridge` then omits record `data` from WebSocket fan-out) |

Event envelope: `PlatformEvent<T>` with `eventId`, `eventType`, `tenantId`, `correlationId`, `timestamp`, `payload`
Publishing: `PlatformEventPublisher` (transport-agnostic interface; the NATS impl is `NatsEventPublisher` in `runtime-messaging-nats`. The former Kafka publisher was removed in Phase 0 — do not reintroduce Kafka.)
Subscriptions: `NatsSubscriptionConfig` registration (broadcast consumers for config changes, queue groups for load-balanced work)
Location: `kelta-platform/runtime/runtime-events/src/main/java/io/kelta/runtime/event/`

## Monitoring & Observability

**Production (Kubernetes — Grafana LGTM stack in `observability` namespace):**

| Tool | Purpose | K8s Address |
|------|---------|-------------|
| Grafana | Dashboards & visualization | `grafana.observability.svc.cluster.local` |
| Tempo | Distributed tracing | `tempo.observability.svc.cluster.local:3200` |
| Loki | Log aggregation | `loki.observability.svc.cluster.local:3100` |
| Mimir | Metrics storage | `mimir.observability.svc.cluster.local:8080` |
| Alloy | OTLP collector | Routes telemetry from services to LGTM backends |

**Local Development (docker-compose `observability` profile):**

| Tool | Purpose | Port |
|------|---------|------|
| Jaeger 2 | Trace UI + OTLP receiver | 16686 (UI), 4317 (gRPC), 4318 (HTTP) |
| OpenSearch | Trace/log/metric storage | 9200 |

**Instrumentation:**

| Component | Details |
|-----------|---------|
| OpenTelemetry Java Agent | v2.25.0, bundled in all service Dockerfiles |
| Spring Boot OpenTelemetry Starter | Auto-instrumentation for traces, metrics |
| Logstash Logback Encoder | v8.0, JSON structured logging |
| OTLP export | HTTP to port 4318 (configurable via `MANAGEMENT_OTLP_METRICS_EXPORT_URL`) |
| Sampling | W3C propagation, 100% by default (configurable via `OTEL_TRACES_SAMPLER_ARG`) |

## Local Development (docker-compose.yml)

| Service | Port | Profile |
|---------|------|---------|
| PostgreSQL | 5432 | default |
| NATS | 4222 | default |
| Redis | 6379 | default |
| Keycloak | 8180 | default |
| Cerbos | 3592/3593 | default |
| Mailpit (SMTP / UI) | 1025 / 8025 | default |
| Jaeger | 16686 | default |
| OpenSearch | 9200 | default |
| NATS Box | 8090 | tools |
| Redis Commander | 8091 | tools |

## Frontend workspace build (`kelta-web` → `kelta-ui/app`)

`kelta-ui/app` consumes the four `@kelta/*` packages in `kelta-web/packages/*` as `file:` deps — `npm install` symlinks them but does **not** build them, so the `dist/` outputs that `package.json`'s `main`/`module`/`types` point at must exist before `tsc -b` can resolve any of them (otherwise: 46 `TS2307` "Cannot find module" errors).

Build order matters because `plugin-sdk` and `components` use `vite-plugin-dts` with `rollupTypes: true`, which resolves re-exported symbols (e.g. `KeltaClient`) from `@kelta/sdk`'s rolled-up `.d.ts` — that file must already exist. `kelta-web`'s root `build` script and `kelta-ui/Dockerfile` both run:

1. `formula` + `sdk` (no internal deps)
2. `plugin-sdk` + `components` (depend on `formula`/`sdk`)

Local dev: `cd kelta-web && npm install && npm run build` once before `cd kelta-ui/app && npm install && npm run build`.

## Flows

### Initial state shape

Every flow execution starts with a canonical state envelope, built by
`InitialStateBuilder` (`kelta-platform/runtime/runtime-core/.../flow/InitialStateBuilder.java`):

```jsonc
{
  "trigger": { "type": "API_INVOCATION" | "SCHEDULED" | "RECORD_CHANGE" | "WEBHOOK" | "NATS_MESSAGE", ... },
  "input":   { ...source data... },   // present for API_INVOCATION / SCHEDULED / WEBHOOK / NATS_MESSAGE
  "record":  { ...record data... },   // present for RECORD_CHANGE only
  "context": { "tenantId": "...", "flowId": "...", "executionId": "...", "userId": "..." }
}
```

JSONPath expressions inside a flow definition (`inputPath`, `outputPath`, condition
expressions) are evaluated against this envelope. **Always read inputs as `$.input.<key>`**
— never `$.<key>`. Reading the bare key skips the envelope, JSONPath silently returns
no value, and the downstream task fails with whatever generic error comes out of its
own input resolution (often a misleading `"Provider … does not exist"` for FETCH-style
tasks, not a clear "missing input" message).

### NATS-triggered flows

A flow with `flow_type = 'NATS_TRIGGERED'` (V153) and trigger config `{ "topic": "<topic>" }`
starts whenever a message is published to `kelta.trigger.<tenantId>.<topic>` (the
KELTA_TRIGGERS JetStream stream; topics may contain dots — the topic is everything after
the tenant token). `NatsTriggerFlowListener` consumes the namespace as a queue group
(one pod starts each execution), caches active configs per tenant (invalidated by
`kelta.config.flow.changed.<tenantId>`), and passes the message body as `$.input`
(`trigger.type = "NATS_MESSAGE"`, with `subject` + `topic`; a non-JSON body arrives as
`{ "raw": "<text>" }`). The body is arbitrary publisher JSON — not a `PlatformEvent`.

### Wait states — persistence and resume

A Wait with `Seconds` ≤ 10 sleeps in-process. Longer, timestamp-based
(`Timestamp`/`TimestampPath`), and event-based (`EventName`) Waits park the execution
WAITING and record a `flow_pending_resume` row (wake time for timed waits, event name for
event waits). The worker's `FlowResumePollerConfig` (10s default,
`kelta.flow.resume.poll-interval-ms`; disable with `kelta.flow.resume.enabled=false`)
claims due rows with `SELECT FOR UPDATE SKIP LOCKED` — exactly one pod resumes an
execution — and `FlowEngine.resumeExecution` re-loads the flow definition and continues
from the Wait's `Next` (a terminal Wait completes). Event-based waits resume through
`claimPendingResumeByEvent` when an event source calls it. Waits inside Parallel/Map
branch sub-definitions are **not** resumable (their state ids aren't in the top-level
definition) — the resume fails the execution with a clear error instead of hanging.

### Manual / MCP / HTTP invocation — the double-wrap rule

`POST /api/flows/{flowId}/execute` (in `FlowExecutionController.executeFlow`) reads its
input from `body.input` and passes it through to `buildFromApiInvocation`, which then
puts it under `state.input`. So the request body itself must already be wrapped under
`input` — the controller does **not** treat the whole body as input.

That means callers double-wrap: the outer wrap is the HTTP request shape, the inner
wrap is the value that ends up at `$.input`.

HTTP:
```bash
curl -X POST $GW/api/flows/$FLOW_ID/execute \
  -H 'Content-Type: application/json' \
  -d '{ "input": { "slug": "acme" } }'
# ⇒ state.input == { "slug": "acme" }, flow reads $.input.slug
```

MCP (`execute_flow` tool — the MCP layer passes the tool's `input` argument straight
through as the HTTP body, so the same double-wrap applies):
```jsonc
execute_flow({
  "flowId": "flow-123",
  "input": { "input": { "slug": "acme" } }   // double wrap: outer = HTTP body, inner = $.input
})
```

A single wrap (`input: { "slug": "acme" }`) sends `{ "slug": "acme" }` as the body,
the controller looks for `body.input` (absent), `$.input` becomes `{}`, and every
`$.input.slug` read returns no value.

Escape hatches in the controller:
- `body.state` — if present, the controller treats it as a pre-built initial state envelope (only `context` is auto-filled). Use this when you need to seed `record`/`headers` keys that `buildFromApiInvocation` doesn't produce.
- `body.test: true` — runs the flow in test mode (`isTest=true`); does not change how `input` is resolved.

### Page-event → flow framing (page builder, slice 2e)

A page-builder `runFlow` action (a button `onClick`, etc.) rides the **same**
`POST /api/flows/{flowId}/execute` double-wrap rule. The editor stores the **inner** input
map on `action.input` (e.g. `{ orderId: {$bind:'record.id'} }`); the client-side action runtime
(`kelta-ui/app/src/pages/PageBuilderPage/runtime/executeAction.ts`) resolves the `{$bind}` values
against the live click scope and sends `{ input: <resolved action.input> }` as the HTTP body — i.e.
it adds the **outer** wrap. So the flow reads `$.input.<key>` exactly as above; the author never
wraps twice. The execute response is async (`200` with `attributes.status:"RUNNING"`); the execution
id is `data.id` (read via `unwrapResource(json).id`). With `awaitResult:true` the runtime polls
`GET /api/flows/executions/{id}` every 1.5 s (up to 60 s) until a terminal status
(`COMPLETED`/`FAILED`/`CANCELLED`) — `FAILED`/`CANCELLED` reject and stop the action chain. This is
the only new flow framing 2e introduces (no new endpoint or subject). No backend change.

### Scheduled (cron) invocation — `triggerConfig.inputData`

For SCHEDULED flows there is no per-run HTTP body, so the static payload lives on the
flow definition itself. `ScheduledJobExecutorService` reads the flow's `trigger_config`
column and hands it to `InitialStateBuilder.buildFromSchedule`, which copies
`triggerConfig.inputData` into `state.input`:

```jsonc
// flow.trigger_config (stored on the flows row)
{
  "type":    "SCHEDULED",
  "cron":    "0 */15 * * * *",
  "timezone":"UTC",
  "inputData": { "slug": "acme", "mode": "full" }
}
// ⇒ state.input == { "slug": "acme", "mode": "full" }, flow reads $.input.slug
```

This is the SCHEDULED-equivalent of the inner wrap in `execute_flow`: the same flow
definition can be driven by cron or by `execute_flow` as long as the static
`triggerConfig.inputData` and the caller-supplied `body.input` produce the same
shape under `$.input`.

## Email (SMTP)

Worker emails go out via `spring-boot-starter-mail`. The kelta-worker `application.yml`
defaults target a local **mailpit** (`localhost:1025`, no auth, no TLS) so
`docker compose up` and bare `mvn spring-boot:run` both work out of the box —
mailpit captures every outbound message and serves them at <http://localhost:8025>.

Toggle via `kelta.email.enabled` (`EMAIL_ENABLED` env, default `true`). Override
the SMTP target in K8s via `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`,
`SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`. Per-tenant SMTP overrides live in
`TenantEmailSettings` and take precedence over the platform-wide config.

**Send paths.** Internal service-to-service delivery uses `InternalEmailController`
(`/api/internal/email/**`, shared-token, not gateway-routed). The FE-reachable
transactional send is `EmailSendController` `POST /api/email/send` (gateway static
route `/api/email/**`) — stored-template-only, `MANAGE_EMAIL_TEMPLATES`-gated,
per-tenant rate-limited; backs the `send_email` UI Quick Action. See
`architecture.md` → *Transactional send endpoint*.

## Mass-email campaigns (V152)

Bulk send on top of the same SMTP path. Three tenant-scoped tables (RLS): `email_campaign`
(the read-only **campaigns** system collection — config + aggregate stats), `email_campaign_recipient`
(per-recipient send + open/click/unsubscribe events), and `email_suppression` (per-tenant
unsubscribe/suppression list).

- **Admin API** — `/api/admin/campaigns` (`CampaignAdminController`), gated on the new
  **`MANAGE_CAMPAIGNS`** system permission (seeded to System Administrator + Marketing User).
  CRUD + actions `/{id}/send` (enqueue now), `/{id}/schedule`, `/{id}/cancel`, `/{id}/stats`,
  `/{id}/recipients`, `/{id}/test`, and `/suppressions` (list/add/remove). The generic collection
  route is read-only, so every mutation goes through this controller.
- **Runner** — `CampaignRunnerService`, polled by `CampaignPollerConfig` (default 15s). Claims
  runnable campaigns (QUEUED, or SCHEDULED whose time has arrived) with a conditional-claim
  `UPDATE ... WHERE status IN (...)` after a `SELECT ... FOR UPDATE SKIP LOCKED` (leader election,
  mirrors `BulkJobProcessorService`). Runs the claim with **no tenant context** (admin_bypass) so it
  sees campaigns across tenants; each campaign's work then runs under `TenantContext.withTenant`.
  Resolves recipients from `target_collection` via the `QueryEngine` (paged), renders subject/body
  with `${field}` merge fields (same grammar as `DefaultEmailService`), and sends via
  `EmailService.queueEmail(..., "CAMPAIGN_SEND", campaignId)`.
- **Tracking** — public, unauthenticated endpoints `/api/track/{open,click,unsubscribe}`
  (`CampaignTrackingController`), added to the gateway `unauthenticated-paths` allowlist. Each link
  carries an **HMAC-signed opaque token** (`TrackingTokenService`, `CAMPAIGN_TRACKING_SECRET`) that
  encodes the recipient id and self-authenticates — a forged/tampered token is rejected, so stats
  can't be poisoned and unsubscribes can't be forged. Bodies get the open pixel appended, `href`s
  rewritten through the click redirect, and an unsubscribe footer (`${unsubscribeUrl}` merge var).
  The click redirect only forwards to `http(s)` URLs (open-redirect guard).
- **Spam controls** — the suppression list is checked before every send; the daily
  `campaignEmailsPerDay` governor limit (`TenantTierQuotas`) caps volume; a configurable
  `send-rate-per-second` throttle paces delivery. Config lives under `kelta.campaigns.*`
  (`CampaignProperties`).

## Sandboxes & metadata promotion (multi-cluster)

Sandbox = a real linked tenant (`tenant.parent_tenant_id`, V158); config moves between
environments as **metadata packages** (`PackageService` export → `PackageImportService` import).

- **Package format v2** — `{formatVersion: 2, source: {instanceId, tenantId, tenantSlug},
  exportedAt, items: [{type, data}]}`. `instanceId` is the installation's stable identity
  (`platform_instance`, V158). Items carry natural-key context (`collection_name`,
  `reference_collection_name`, `layout_name`, `field_name`, `picklist_name`, `menu_name`) so
  cross-tenant/cross-cluster import remaps every reference **by name, never by UUID**.
  Importers accept v1 packages (minus the new types).
- **Topology rule** — a tenant's dev/qa/staging/prod may span isolated clusters whose databases
  don't know each other. The only universal identity check is **source ≠ target**: promotion
  rejects a package whose `source.instanceId` + `source.tenantId` equal the local target.
  Generic `/api/packages/import` still allows same-tenant reapply (restore).
- **Remote push promotion** — an environment row may describe a remote target
  (`remote_base_url`, `remote_tenant_slug`, `credential_ref`). `RemotePromotionClient` POSTs the
  package to `{base}/{slug}/api/packages/import?conflictMode=` with a **vault PAT**
  (`CredentialProvider`; the secret never lives on the env row). The remote PAT user needs
  `CUSTOMIZE_APPLICATION` on the remote cluster. URL scheme allow-list http/https, no userinfo,
  redirects disabled, bounded timeouts/response. Remote targets have no local snapshot —
  rollback → 409; restore on the remote side.
- **Manual cross-cluster path** — `kelta metadata export` on cluster A → move the file →
  `kelta metadata apply` on cluster B (same hardened import + provenance).
- **Security types** (`ROLE`/`POLICY`/`ROUTE_POLICY`/`FIELD_POLICY`) are cloned INTO sandboxes
  but **never promoted** — authz changes reach production only via the explicit packages API.
