# Capability Status Map

What actually works vs. what is a stub. **Check this before touching a subsystem** — the
biggest trap in this codebase is features whose **UI is fully built but backend is missing**.
Treat each row as the current contract; verify against code, and update this file when you
ship a change that moves a row.

## ✅ Complete (backend + UI, production-ready)

| Capability | Notes / key classes |
|------------|---------------------|
| Dynamic schema engine | Full field-type set (primitives, date/time, JSON/array, picklists, currency, percent, auto-number, phone/email/URL, rich text, encrypted, external ID, geolocation, relationship — see `FieldType` enum); LOOKUP (FK SET NULL) + MASTER_DETAIL (FK CASCADE); FORMULA + ROLLUP_SUMMARY computed fields; auto-number sequences; schema snapshots |
| Auto-generated JSON:API | Full CRUD per collection, includes (multi-level + Redis cache), sparse fieldsets, filter/sort/paginate, sub-resources, atomic ops `POST /{tenant}/_operations`, dynamic route registration via NATS |
| Global full-text search | Postgres `tsvector`, `GET /api/_search?q=`, per-field `searchable`, RLS-isolated, NATS-driven index maintenance |
| Auth / identity | Internal OIDC provider (`kelta-auth`, Spring Authorization Server); federated brokering (`FederatedUserMapper`); dynamic JWKS by issuer (Redis 15-min TTL); JDBC sessions; password reset/forced-change |
| MFA | TOTP (RFC 6238) + recovery codes + SMS OTP; encrypted secrets; rate-limited; `MfaController` `/api/auth/mfa/*`; `SmsProvider` SPI |
| Password policies | Per-tenant length/complexity + account lockout |
| Personal Access Tokens | `klt_` + 40 chars, SHA-256 hashed; `/api/me/tokens` (max 10); Redis revocation (`pat:revoked:`) |
| RBAC | Profiles + permission sets + groups; most-permissive-wins; per-profile ABAC CEL rules; 7 built-in profiles |
| Cerbos authz | Per-tenant policy generation/sync; gateway `RouteAuthorizationFilter` (route) + worker `CerbosRecordAuthorizationAdvice` (record); fail-closed circuit breaker (3 fails → open 10s) |
| **Field-Level Security (read + write)** | Write: `CerbosFieldWriteSecurityAdvice` blocks HIDDEN on create/update. **Read: `CerbosFieldSecurityAdvice` strips denied `attributes` + to-one `relationships`** (shipped #1038/627d4ca9). System audit fields never stripped |
| Data security | AES-256-GCM field encryption (per-tenant key derivation); Postgres RLS on all system tables; per-tenant schema isolation; OpenSearch audit trails |
| Multi-tenancy & governors | URL slug resolution + custom domains (`CustomDomainFilter`); per-tenant governor limits (API/day, storage, users, collections, fields, workflows, reports) with live usage |
| Visual Flow Builder | 8 state types, 16 action handlers, JSONPath data flow, durable execution (survives restart), retry/catch, versioning, React Flow designer. **Triggers working: record / API-webhook / scheduled-cron.** Adding a handler also needs UI wiring (hardcoded `RESOURCE_GROUPS` + `*Params.tsx`; the `ActionHandlerDescriptor` SPI is **not** consumed by the builder) — see `playbooks.md`. (TODOs below) |
| MCP server (admin + user toolsets) | Built. kelta-admin (schema/field/flow/layout/picklist/validation-rule tools) + kelta-user (records/flows/approvals/search). **Many tools already exist — check `kelta-mcp/.../tool/{admin,user}/` before adding one** (e.g. `create_validation_rule` and the `validation-rules` system collection are already implemented). |
| Rate limiting | Per-tenant sliding window (Redis) + daily governor quota + IP-based; fail-open if Redis down |
| Observability | OTel → Jaeger → OpenSearch tracing; `OpenSearchLogAppender`; metrics dashboard; audit; Prometheus `kelta_worker_*` metrics; 7 admin monitoring pages; per-tenant retention |
| Integration | HTTP callout, outbound message, NATS publish, inbound webhooks (`POST /api/webhooks/{flowId}`), Svix outbound, SMTP email, `PushProvider` SPI, Superset analytics, merge-field templating |
| Embedded analytics (Superset) | Guest tokens, dashboard discovery, dataset sync (NATS-triggered), per-tenant isolation, `SupersetEmbed` |
| WebSocket realtime | `/ws/realtime` (JWT on upgrade), `RealtimeBridge` ← `kelta.record.changed`, per-session subscription limits, tenant-scoped |
| Image transforms | `GET /api/images/{path}?w=&h=&fit=&format=&quality=`; resize/crop/convert; bomb protection |
| Direct file serving | `GET /api/files/{path}` with range support, tenant-scoped |
| Notes & attachments | Full S3 lifecycle: presigned-PUT upload + finalize, list/download (`AttachmentUrlEnricher`), delete with S3 cleanup, `AttachmentCleanupHook` cascade on parent delete (idx `idx_attachment_tenant_record`, V142) |
| Metadata dependency & impact | `MetadataDependencyService` per-tenant graph; `GET /api/metadata/impact?type=&id=&direction=` and `/api/metadata/graph`; Tarjan SCC cycle detection (#1039) |
| Config-health governance (backend) | `ConfigHealthAnalyzer` scans tenant config via pluggable `HealthRule` beans (circular master-detail, collections without layouts/fields, orphan fields, flows without error handling, over-permissive profiles) → 0–100 score + findings; `GET /api/config-health`. UI dashboard + AiProposal one-click remediation pending |
| Developer experience | `@kelta/sdk` (`KeltaClient`, `QueryBuilder`), `@kelta/cli`, OpenAPI 3.0 + Swagger at `/api/docs`, Zod schemas, i18n (6 langs, RTL), theme, a11y, 70+ UI pages |
| Operational | Graceful shutdown (persist in-flight flows), optimistic locking for scheduled tasks, multi-layer cache (Caffeine→Redis→worker) w/ NATS invalidation, security headers, health endpoints |

## 🟡 Partial — backend exists, gaps remain

| Capability | Works | Missing |
|------------|-------|---------|
| Visual Flow triggers | record / API-webhook / scheduled-cron | **NATS_TRIGGERED**: no dynamic per-flow consumer. **Wait-state resume**: `resumeExecution()` unimplemented — long Waits persist WAITING but never resume |
| Extensibility / modules | `KeltaModule` SPI, 3 compile-time modules, per-tenant lifecycle w/ NATS propagation, `@kelta/plugin-sdk` | Runtime JAR loading returns placeholder responses (needs ClassLoader + sandbox + S3); admin UI doesn't query `ComponentRegistry` to render plugin components |
| Integration | (see Complete) | **Script execution**: `ScriptExecutor` SPI is a no-op (no GraalVM/JS engine). Connected Apps: client-credentials works, full auth-code flow not |
| Scheduled jobs | Flow-type cron triggers work | General `scheduled_jobs` dispatcher for SCRIPT / REPORT_EXPORT job types not built |
| Page builder / screen builder | `PageBuilderPage` editor + CRUD; `ui-pages` has `slug`/`published`/`config` (V116); **render contract**: `GET /api/pages/{slug}/render` (`PageRenderController`/`PageRenderService`) serves a published+active page as a **versioned** `PageRenderContract` (`{version, slug, title, path, tree}`); gateway static route `/api/pages/**` registered (Rec 1 slice 1a) | end-user shell route + schema-driven renderer mapping the tree → `@kelta/components` (slice 1c); builder-canvas binding panels (slice 1d); `kelta.config.page.changed` broadcast + per-page Cerbos authz (currently published+active+tenant is the visibility gate) |
| Governed AI agents | **Definition model + CRUD** (`AgentDefinition` → `ai_agent`, kelta-ai V4; `AgentService`/`AgentDefinitionRepository`/`AgentController` at `/api/ai/agents`). **Orchestration runtime**: `AgentRuntimeService` runs a bounded tool-use loop (`AgentModelClient` seam over `AnthropicService` attaches only the agent's allowed tool subset + model/maxTokens overrides; `ToolDispatcher` executes; non-permitted tools refused as defense-in-depth) with per-run iteration (8) + token (100k) caps, monthly-quota check + `recordUsage` per turn; `POST /api/ai/agents/{id}/run` returns final text + tool-call trace. **Audit + authz**: every run (incl. refusals) is persisted to `ai_agent_execution` (kelta-ai V5; tool-call trace + tokens + status) via `AgentExecutionService`, listable at `GET /api/ai/agents/{id}/executions`; runs require `X-User-Id` so tools execute as the invoking user — the worker's per-record Cerbos/FLS advice then applies downstream (no privilege escalation). **PII masking**: `PiiMaskingService` redacts email/SSN/card/phone from tool results before they reach the model or the audit trail (always-on; user input is not masked). **Admin UI**: `AiAgentsPage` (`/ai-agents`, Setup → Automation) — list/create/edit/delete + a run panel showing final text + tool trace + tokens; Vitest-covered (Playwright e2e follows once the page is deployed — the e2e suite runs against the live env) | — (stretch: `MCP_CALL` flow action, hybrid tsvector+vector ranking) |
| Semantic search / RAG | `EmbeddingService` SPI + dependency-free `HashingEmbeddingService` default (overridable via `@ConditionalOnMissingBean`); `VECTOR` field maps to `vector(N)`; **pgvector enabled** (lazy `CREATE EXTENSION` on first VECTOR column; `?::vector` writes; pgvector image for local/CI — prod Postgres needs the extension installed); `StorageAdapter.semanticSearch` cosine (`<=>`) query + `SemanticSearchService` + `POST /api/{collection}/semantic-search` (FLS-respecting; distances in `meta`); **HNSW cosine index auto-emitted**; `semantic_search` MCP user tool; **embed-on-write**: `EmbeddingOnWriteHook` (wildcard before-save) auto-populates a VECTOR field from a configured `embeddingSource` text field via the `EmbeddingService` (dimension-guarded; explicit vectors and untouched source skipped); end-to-end verified against real pgvector (`PhysicalTableStorageAdapterVectorIntegrationTest`) | governed agent runtime (next Rec 3 slice) |

## 🔴 UI Complete, backend MISSING (do not assume these work end-to-end)

| Capability | Built (UI) | Not built (backend) |
|------------|-----------|---------------------|
| Reports & dashboards | Multi-step report builder + dashboard editor | No query-execution engine reading `columns`/`filters`/`groupings`; no chart-data endpoint; no CSV/PDF export |
| Approval processes | Process defs, steps, assignees, field updates | No submit/approve/reject endpoints; no record locking; no `SUBMIT_FOR_APPROVAL` action handler |
| Bulk data operations | INSERT/UPDATE/UPSERT/DELETE job UI + progress | No worker batch processor; no file upload/download for payloads |
| Configuration packages | Export/import wizard, dry-run preview, history | **No `PackageController`/`PackageService`** — UI calls `/api/packages/*` which 404 |
| Record types | Data model + Record Type editor | No runtime enforcement of picklist restriction or layout association |

## ⚪ Planned / enterprise gaps (not started)

SCIM 2.0 provisioning · HA/failover docs (RTO/RPO) · scheduled data export & backup ·
sandbox environments + metadata promotion · data masking · IP allowlisting per tenant ·
delegated administration.

---

This map is distilled from the engineering capability inventory. When you implement or
remove a feature, move its row to the correct section in the same PR so the next agent
trusts it.
