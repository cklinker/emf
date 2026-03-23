# Kelta — The Enterprise Application Platform

**Build enterprise applications in hours, not months. No vendor lock-in. No per-seat pricing trap.**

Kelta is a metadata-driven platform that lets you define your entire data model, business logic, security, and integrations at runtime. Create collections, add fields, wire up workflows, and configure permissions — all without redeployments, migrations, or waiting on engineering.

Self-hosted, open-source, and built for engineering leaders who need the power of platforms like Salesforce without the complexity, cost, or lock-in.

---

## Why Kelta

### vs. Salesforce

- **No per-seat licensing** — self-hosted on your infrastructure, no $300+/user/month
- **No proprietary languages** — standard REST APIs, TypeScript SDK, and JSON:API instead of Apex and SOQL
- **Real access control** — RBAC with profiles, permission sets, groups, and Cerbos-powered ABAC policy evaluation
- **Deploy anywhere** — your cloud, on-prem, air-gapped — you own the infrastructure and the data
- **Open standards** — JSON:API, OpenAPI 3.0, OAuth 2.0, OIDC, RFC 6238 (TOTP)

### vs. Airtable and Low-Code Tools

- **Enterprise-grade security** — field-level encryption (AES-256-GCM), Row Level Security, MFA, per-tenant password policies
- **Real workflow engine** — a full state machine with 8 state types, durable execution, retry policies, and error handling — not simple automations
- **27 field types** — including encrypted, geolocation, currency, auto-number, rich text, and relationship fields
- **Full API-first architecture** — JSON:API compliant with filtering, sorting, pagination, includes, atomic operations, and WebSocket realtime
- **No row limits, no record caps** — PostgreSQL-backed, scales with your data

### vs. Building from Scratch

- **111 database migrations you don't have to write** — schema, security, audit, search, tenancy, and more
- **16 pre-built workflow action handlers** — data operations, email, HTTP callouts, notifications, scripting, and event publishing
- **70+ admin pages ready to go** — user management, schema editor, flow designer, monitoring dashboards, security audit
- **Authentication solved** — internal OIDC provider, federated identity brokering, MFA (TOTP + SMS), password policies, personal access tokens
- **Security engineering done** — CSP headers, CORS, encrypted sessions, rate limiting, audit trails, field-level encryption, Row Level Security

---

## Key Capabilities

### Define Your Data Model at Runtime

Create collections (tables), add fields, define relationships, and set validation rules — all through the API or admin UI. The platform handles storage, indexing, and API generation automatically.

- **27 field types** covering every enterprise need: primitives, date/time, structured data (JSON, arrays), picklists (single and multi-select), currency, percent, auto-number, phone, email, URL, rich text, encrypted, external ID, geolocation, and relationship fields
- **Relationships** with automatic foreign key management: LOOKUP (optional, SET NULL on delete) and MASTER_DETAIL (required, CASCADE on delete)
- **Computed fields**: Formula expressions (no physical column) and rollup summaries (COUNT, SUM, MIN, MAX, AVG across child records)
- **Validation rules**: Field-level constraints (min/max, regex, nullable) plus collection-level rules using a formula expression language
- **Auto-number sequences**: Configurable prefix and padding (e.g., `CASE-0001`) backed by PostgreSQL sequences
- **Schema versioning**: Immutable snapshots tracked across every change

### Instant API for Everything

Every collection automatically gets a fully compliant JSON:API endpoint. No boilerplate, no code generation — define your schema and the API is live.

- **Full CRUD** at `/{tenant}/{collection}` with relationship includes, sparse fieldsets, filtering, sorting, and pagination
- **Atomic operations** for bulk create/update/delete in a single transactional request
- **WebSocket realtime subscriptions** for live record change notifications with Kafka bridge and tenant isolation
- **Auto-generated OpenAPI 3.0 documentation** with embedded Swagger UI — dynamically reflecting your collection schema
- **Global full-text search** across all collections via PostgreSQL tsvector with per-field indexing control
- **Dynamic route registration** — schema changes update gateway routes in real-time via Kafka, no restart required

### Visual Workflow Builder

A full state-machine automation engine inspired by AWS Step Functions, with a visual drag-and-drop designer.

- **8 state types**: Task, Choice, Parallel, Map, Wait, Pass, Succeed, Fail
- **16 built-in action handlers**: field updates, record CRUD, query records, email alerts, notifications, HTTP callouts, outbound messages, script invocation, event publishing, and flow triggering
- **Durable execution**: Every state transition persisted to database — survives pod restarts
- **Triggers**: Record-triggered (on create/update/delete with filter formulas), API/webhook-triggered, and scheduled (cron with timezone)
- **Data flow**: Full JSONPath support with InputPath, ResultPath, and OutputPath at every state transition
- **Error handling**: Retry with exponential backoff, catch-and-redirect error handling per state
- **Visual designer**: React Flow-based canvas with drag-and-drop composition, step palette, properties panel, test execution, and execution history with visual timeline

### Enterprise-Grade Security

Defense in depth at every layer — authentication, authorization, encryption, and audit.

- **Internal OIDC provider** (kelta-auth) — no external identity server required. Built on Spring Authorization Server.
- **Federated identity brokering** — connect Google, Okta, Azure AD, or any OIDC provider for SSO
- **Multi-factor authentication** — TOTP (RFC 6238) with QR code enrollment and recovery codes, plus SMS OTP with rate limiting
- **Per-tenant password policies** — configurable requirements and account lockout rules
- **Personal access tokens** — `klt_` prefixed, SHA-256 hashed, max 10 per user, Redis-backed revocation
- **RBAC with Cerbos Policy Engine** — profiles, permission sets, groups, custom ABAC rules with CEL expressions, enforced at gateway and service layers
- **Field-level encryption** — AES-256-GCM at the application layer with per-tenant key derivation
- **Row Level Security** — PostgreSQL RLS on every table, enforced via session variables per request
- **Schema isolation** — tenant data in dedicated PostgreSQL schemas
- **Security hardening** — CSP headers, strict CORS, encrypted session cookies, JSON-only error responses, dependency scanning

### Multi-Tenant from Day One

Every request is scoped to a tenant. Every resource is quota-controlled. Built for SaaS from the ground up.

- **Flexible tenant routing** — URL-based (`/{tenantSlug}/api/...`) or custom domains (CNAME-based, e.g., `app.acme.com`)
- **PostgreSQL schema isolation** — tenant data lives in dedicated schemas with Row Level Security on system tables
- **Governor limits** with real-time usage tracking:

| Resource | Default Limit |
|----------|--------------|
| API calls per day | 100,000 |
| Storage | 10 GB |
| Users | 100 |
| Collections | 200 |
| Fields per collection | 500 |
| Workflows | 50 |
| Reports | 200 |

### Full Observability Stack

Know exactly what's happening in your platform at all times.

- **Distributed tracing** — OpenTelemetry agent to Jaeger collector to OpenSearch, with request/response body capture and sensitive field sanitization
- **Log aggregation** — structured logs shipped to OpenSearch with MDC enrichment (traceId, tenantId, userId)
- **Metrics dashboard** — request counts, error rates, latency percentiles (P50, P75, P95, P99), top endpoints, auth failures
- **Audit trails** — setup changes, security events, login history with before/after values, all shipped to OpenSearch
- **Prometheus metrics** — request counts, latency histograms, error counters, and active collection gauges for HPA autoscaling
- **7 monitoring pages** in the admin UI — requests, logs, errors, performance, user activity, metrics, and observability settings

### Integration-Ready

Connect Kelta to your existing systems with standards-based integrations.

- **HTTP callouts** from flows with header, method, and body templating via merge fields
- **Inbound webhooks** — dedicated endpoints per flow with request body mapping and header capture
- **Outbound webhooks** powered by Svix with event type mapping, HMAC verification, and collection-scoped filtering
- **Kafka event streaming** — publish custom events to arbitrary topics from any flow
- **Email delivery** — per-tenant SMTP with async delivery, email logging, and template management
- **Push notifications** — SPI with device registration, ready for FCM/APNs integration
- **Embedded analytics** — Apache Superset integration with guest tokens, automatic dataset sync, and tenant-isolated dashboards
- **Image transformations** — on-the-fly resize, crop, and format conversion via URL parameters with security protections

---

## Developer Experience

Everything developers need to build on top of Kelta.

- **TypeScript SDK** (`@kelta/sdk`) — `KeltaClient` with auto-discovery, token management, retry with exponential backoff, and fluent `QueryBuilder<T>` for filtering, pagination, sorting, and includes
- **Admin Client** — typed access to 140+ admin types: collections, fields, roles, policies, webhooks, tenants, governor limits, and more
- **CLI tool** (`@kelta/cli`) — command-line interface for collection and record management with config file at `~/.keltarc`
- **Auto-generated OpenAPI docs** — Swagger UI at `/api/docs` dynamically reflecting your collection schema
- **Plugin SDK** (`@kelta/plugin-sdk`) — `BasePlugin` lifecycle with `ComponentRegistry` for custom field renderers and page components
- **Module SPI** — `KeltaModule` interface for backend extensions with action handlers, before-save hooks, and lifecycle events
- **React component library** (`@kelta/components`) — `DataTable`, `ResourceForm`, `FilterBuilder`, layout components, and hooks (`useResource`, `useResourceList`, `useDiscovery`)
- **6 languages** — English, Spanish, French, German, Portuguese, Arabic with RTL support
- **Dark/light theme** with WCAG 2.1 Level AA accessibility — skip links, ARIA live regions, keyboard navigation, screen reader support
- **OpenTelemetry instrumentation** — frontend telemetry for end-to-end distributed tracing

---

## By the Numbers

| Metric | Value |
|--------|-------|
| Field types | 27 |
| Admin and end-user UI pages | 70+ |
| Workflow action handlers | 16 |
| Workflow state types | 8 |
| Database migrations | 111 |
| Supported languages | 6 (with RTL) |
| Built-in permission profiles | 7 |
| System permission types | 15 |
| SDK packages | 4 (@kelta/sdk, components, plugin-sdk, cli) |
| UI component library items | 33 (shadcn/ui + Radix) |
| Monitoring pages | 7 |
| Governor limit categories | 7 |

---

## Architecture at a Glance

| Service | Technology | Purpose |
|---------|-----------|---------|
| **Gateway** | Java 21, Spring Cloud Gateway | API ingress, JWT/PAT auth, tenant routing, rate limiting, WebSocket realtime, Cerbos authorization |
| **Auth** | Java 21, Spring Authorization Server | Internal OIDC provider, identity federation, MFA, password policies, sessions |
| **Worker** | Java 21, Spring Boot 3.2 | Business logic, CRUD, flows, schema, integrations, email, image transforms |
| **Admin UI** | React 19, Vite, TypeScript, Tailwind | 70+ page admin console and end-user application runtime |
| **SDK** | TypeScript | Client library, components, plugin SDK, CLI |
| **PostgreSQL** | v15 | Primary data store with RLS, schema isolation, full-text search |
| **Redis** | v7 | Rate limiting, caching, PAT revocation, session storage |
| **Kafka** | v3.7 (KRaft) | Event streaming, realtime bridge, config propagation |
| **Cerbos** | PDP | Fine-grained ABAC policy engine with per-tenant policies |
| **OpenSearch** | + Jaeger, OpenTelemetry | Traces, logs, audit events, metrics |
| **Superset** | Apache | Embedded analytics dashboards |
| **Svix** | | Outbound webhook delivery |

---

## Deployment Flexibility

- **Self-hosted** on any Kubernetes cluster — no mandatory cloud vendor dependency
- **ArgoCD-ready** deployment manifests for GitOps workflows
- **Docker containers** for all services (gateway, auth, worker, UI)
- **Air-gap friendly** — no external SaaS dependencies required for core functionality
- **Infrastructure-agnostic** — runs on AWS, GCP, Azure, bare metal, or your homelab

---

## Roadmap Highlights

Capabilities actively being developed or planned:

- **Report execution engine** with query builder and CSV/PDF export
- **Approval workflow engine** with submit/approve/reject logic and record locking
- **Bulk data import/export** with batch processing and progress tracking
- **Configuration packages** for metadata promotion between environments
- **SCIM 2.0 provisioning** for automated user/group sync from identity providers
- **Server-side scripting** via GraalVM for custom business logic
- **Runtime module loading** for tenant-scoped backend extensions via JAR upload
- **Full OAuth authorization code flow** for third-party connected app integrations
