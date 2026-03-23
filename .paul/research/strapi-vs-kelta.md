# Research: Strapi vs Kelta Feature Comparison

**Date:** 2026-03-22
**Type:** Web
**Purpose:** Evaluate Strapi strengths/weaknesses compared to Kelta

## Executive Summary

Strapi is a **CMS-first headless content management system** optimized for content teams and front-end developers. Kelta is an **enterprise application platform** for multi-tenant SaaS with complex business logic. They serve fundamentally different primary use cases but overlap in data modeling, API generation, and admin UIs.

**Strapi's strengths:** Content authoring UX (draft/publish, content history, i18n), GraphQL API, plugin ecosystem/marketplace, developer onboarding speed, media management (Cloudinary integration, responsive images), internationalization.

**Kelta's strengths:** Multi-tenancy (native per-tenant schemas), Cerbos RBAC/ABAC, event-driven architecture (Kafka), observability (OpenTelemetry + 7 dashboard pages), Svix webhooks, 27 enterprise field types, visual flow engine, API gateway (Spring Cloud), microservices architecture.

---

## Feature Comparison

### 1. Data Model

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| Field Types | 27 (formula, rollup, currency, encrypted, auto-number, etc.) | ~15 basic (text, number, date, media, relation, component, dynamic zone) | **Kelta** |
| Visual Builder | Yes (collection/field config UI) | Yes (Content-Type Builder — more mature) | **Strapi** |
| Relationships | Reference, Master-Detail, Lookup with cascade | 6 types (one-way, 1:1, 1:N, N:1, N:N, many-way) | Comparable |
| Components/Reuse | No reusable field groups | Yes (Components + Dynamic Zones) | **Strapi** |
| Validation | Field + collection-level formula validation | Basic constraints (required, min/max, regex) | **Kelta** |
| Computed Fields | Formula + Rollup Summary | No | **Kelta** |
| Draft/Publish | No | Yes (built-in) | **Strapi** |
| Content History | No (audit trail via Kafka) | Yes (paid — Growth/Enterprise) | **Strapi** |
| i18n | No | Yes (core, free) | **Strapi** |
| Field Encryption | AES-256-GCM per-tenant | No | **Kelta** |

### 2. Authentication

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| OIDC Federation | Yes (any provider, kelta-auth service) | OAuth2 providers for end-users only | **Kelta** |
| Admin SSO | Via OIDC | SAML/OIDC (Enterprise paid) | **Kelta** (free) |
| API Tokens | No | Yes (3 types: read-only, full, custom) | **Strapi** |
| JWT Auth | Yes | Yes | Tie |
| Email/Password | Yes | Yes | Tie |

### 3. Authorization

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| RBAC | Cerbos-backed profiles, groups, 15 system permissions | Basic 3 roles (free), custom roles (Enterprise paid) | **Kelta** |
| ABAC / Policies | Yes (Cerbos CEL rules) | Custom conditions (Enterprise paid) | **Kelta** |
| Field Permissions | Yes (visible/read-only/hidden) | Enterprise paid only | **Kelta** |
| Object Permissions | Yes (per-collection CRUD) | Yes (per content-type) | Tie |
| Auth Testing UI | Yes (what-if scenarios) | No | **Kelta** |

### 4. API Layer

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| REST API | Yes (JSON:API spec) | Yes (custom format) | **Kelta** (standards) |
| GraphQL | No | Yes (plugin, free) | **Strapi** |
| Filter Operators | Standard set | 22+ operators | **Strapi** |
| API Customization | Spring controllers/filters | Custom routes/controllers/policies/middlewares | Comparable |
| Population/Includes | Yes (transitive resolution) | Yes (nested populate) | Comparable |

### 5. Storage / Media

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| File Storage | S3 presigned URLs | Local, S3, Cloudinary, community providers | **Strapi** |
| Media Library UI | No (file metadata only) | Yes (folders, search, bulk ops, responsive images) | **Strapi** |
| Image Transforms | No | Yes (via Cloudinary, responsive sizes) | **Strapi** |

### 6. Automation

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| Flow Engine | Yes (state machine, 15+ handlers, visual designer) | No | **Kelta** |
| Lifecycle Hooks | Kafka events + Spring lifecycle | Document + Query Engine hooks (very granular) | Comparable |
| Serverless Functions | No | No (not a Strapi feature) | Tie |
| Event Bus | Kafka (distributed) | Synchronous hooks only | **Kelta** |

### 7. Webhooks

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| Outbound | Svix (delivery guarantees, retries, consumer portal) | Basic (no retries, no delivery guarantees) | **Kelta** |
| Inbound | Yes (flow triggers) | No | **Kelta** |

### 8. Multi-Tenancy

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| Native Support | Yes (per-tenant schemas, RLS, governor limits) | **No native support** — workarounds only | **Kelta** (decisive) |
| Tenant Provisioning | Automated (lifecycle hooks) | Manual (separate instances) | **Kelta** |

### 9. Observability

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| Distributed Tracing | OpenTelemetry → Jaeger → OpenSearch | No | **Kelta** |
| Log Aggregation | OpenSearch with custom appender | Console logging only | **Kelta** |
| Metrics | Prometheus + 7 dashboard pages | No built-in | **Kelta** |
| Audit Trail | Setup, security, login history | Enterprise paid only | **Kelta** |
| Health Checks | K8s probes built-in | Community plugin only | **Kelta** |

### 10. Developer Experience

| Feature | Kelta | Strapi | Winner |
|---------|-------|--------|--------|
| TypeScript SDK | Yes | Yes (official client) | Tie |
| Multi-Language SDKs | TypeScript only | Community (Dart, Python) | Slight **Strapi** |
| CLI | Type generator only | Full scaffolding/generation CLI | **Strapi** |
| Plugin Marketplace | No | Yes (active) | **Strapi** |
| Documentation | Internal only | Comprehensive public docs + community | **Strapi** |
| Onboarding Speed | Enterprise setup required | `npx create-strapi@latest` | **Strapi** |

### 11. Enterprise Pricing Model

| Feature | Strapi Free | Strapi Paid | Kelta |
|---------|-------------|-------------|-------|
| Custom Roles | No | Enterprise | Yes (free) |
| Field Permissions | No | Enterprise | Yes (free) |
| SSO/OIDC | No | Growth add-on or Enterprise | Yes (free) |
| Audit Logs | No | Enterprise | Yes (free) |
| Content History | No | Growth+ | N/A |
| Review Workflows | No | Growth+ | N/A (flows serve different purpose) |

**Key insight:** Many features Strapi charges for at Enterprise tier are built into Kelta's core.

---

## Lessons from Strapi for Kelta's Roadmap

### Features Worth Adopting

1. **Draft/Publish Workflow** — Valuable for content-heavy use cases. Could be added as a collection-level option (enable/disable per collection).

2. **Content History / Version Tracking** — Audit trail exists via Kafka but no UI for viewing/restoring previous record versions.

3. **Internationalization (i18n)** — If Kelta targets global enterprises, per-field localization is valuable.

4. **Components / Reusable Field Groups** — Dynamic Zones and Components allow flexible content modeling without new collections.

5. **GraphQL API** — Frequently requested by frontend developers as alternative to REST.

6. **Plugin Marketplace** — Kelta's module system exists but no discovery/installation UX.

7. **Media Library UI** — Richer file management experience beyond S3 presigned URLs.

### Features NOT Worth Adopting (Different Domain)

- Strapi's CMS-specific features (rich text blocks editor, SEO plugin, etc.)
- Strapi's monolithic Node.js architecture
- Strapi's dual user model (admin vs API users)

---

## Combined Insights (Appwrite + Strapi + Kelta)

### Gaps confirmed by BOTH competitors

These gaps appear in both Appwrite and Strapi comparisons, making them high-priority:

| Gap | In Appwrite? | In Strapi? | Priority |
|-----|-------------|------------|----------|
| **GraphQL API** | Yes | Yes | High |
| **MFA / TOTP** | Yes | No (admin SSO only) | High |
| **API Keys / PATs** | Yes | Yes (3 types) | High |
| **Email Delivery** | Yes | No (not Strapi's concern) | Critical |
| **Image Transforms** | Yes | Yes (Cloudinary) | Medium |
| **Batch Operations** | Yes | No | Medium |
| **WebSocket Realtime** | Yes | No | Medium |
| **CLI Tool** | Yes | Yes (full CLI) | Medium |

### Kelta's confirmed unique strengths (neither competitor has)

1. Visual flow engine with durable state machine
2. Cerbos RBAC/ABAC with policy-as-code
3. Native multi-tenancy with per-tenant schemas
4. 27 enterprise field types (formula, rollup, currency, encrypted)
5. Comprehensive observability stack (OTel + OpenSearch + Superset)
6. Kafka event-driven architecture
7. Svix webhook management with delivery guarantees

---

*Research completed: 2026-03-22*
*Sources: docs.strapi.io, strapi.io/pricing, strapi.io/enterprise, market.strapi.io*
