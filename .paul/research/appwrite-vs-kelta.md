# Research: Appwrite vs Kelta Feature Comparison

**Date:** 2026-03-22
**Type:** Web + Codebase
**Purpose:** Identify feature gaps between Appwrite and Kelta to inform roadmap planning

## Executive Summary

Kelta is an **enterprise-grade metadata-driven platform** with strong multi-tenancy, authorization (Cerbos), observability, and a visual flow engine. Appwrite is a **developer-friendly backend-as-a-service** with broader authentication options, realtime capabilities, messaging, and serverless functions.

**Kelta's advantages:** Multi-tenancy, Cerbos RBAC/ABAC, flow engine (state machine), 27 typed fields, field encryption, Superset analytics, comprehensive observability (traces/logs/metrics/audit).

**Appwrite's advantages:** Realtime (WebSocket), messaging (email/SMS/push), serverless functions (15+ runtimes), broader auth (30+ OAuth providers, MFA, magic links), GraphQL API, image transformations, offline sync.

---

## Feature-by-Feature Comparison

### 1. Authentication & Users

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Email/Password | Yes | Yes | No |
| OIDC/OAuth2 Federation | Yes (any OIDC provider) | Yes (30+ built-in providers) | **Minor** - Kelta supports any OIDC but lacks preconfigured shortcuts |
| Magic URL / Email OTP | No | Yes | **Gap** |
| Phone/SMS Auth | No | Yes | **Gap** |
| Anonymous Sessions | No | Yes | **Gap** |
| MFA / TOTP | No | Yes | **Gap** |
| Teams/Groups | Yes (groups + profiles) | Yes (teams + roles) | No |
| API Keys / PATs | No | Yes (scoped API keys) | **Gap** |
| Session Management | Yes | Yes (configurable limits) | No |
| Password Policy | Basic (min 8 chars) | Advanced (history, dictionary, personal data check) | **Gap** |
| JWT Tokens | Yes | Yes | No |
| Custom Token (passkeys) | No | Yes | **Gap** |

**Priority gaps:** MFA/TOTP, API keys, enhanced password policies

### 2. Database / Data Model

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Typed Fields | 27 types (rich: currency, formula, rollup, encrypted, geolocation) | 14 types (basic: string, int, float, bool, datetime, email, URL, IP, enum, geo, relationship) | **Kelta ahead** |
| Relationships | Master-Detail, Lookup with cascade options | One-to-one, one-to-many, many-to-many | Comparable |
| Query Engine | Filtering, sorting, pagination, field selection, includes | Full query operators, cursor pagination, geo queries | Comparable |
| Validation Rules | Field-level + collection-level formula validation | Attribute constraints only | **Kelta ahead** |
| Computed Fields | Formula + Rollup Summary | No | **Kelta ahead** |
| Auto-Number | Yes | No | **Kelta ahead** |
| Field Encryption | AES-256-GCM per-tenant | At-rest only | **Kelta ahead** |
| Batch Operations | No | Yes (bulk create/update/delete) | **Gap** |
| GraphQL API | No (JSON:API only) | Yes | **Gap** |
| Full-Text Search | PostgreSQL tsvector | Basic contains/search operator | **Kelta ahead** |
| Offline Sync | No | Yes | **Gap** |
| Transactions | No | Yes | **Gap** |

**Priority gaps:** Batch operations, GraphQL API

### 3. Storage

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| File Upload/Download | S3 presigned URLs | Direct upload with chunking | Comparable |
| Buckets | Per-tenant path isolation | Named buckets with settings | Comparable |
| File Permissions | Via collection-level auth | Per-file and per-bucket | **Gap** |
| Image Transformations | No | Yes (resize, crop, format convert) | **Gap** |
| Direct File Serving | No (presigned URLs only) | Yes (direct download endpoint) | **Gap** |
| Multiple Storage Backends | S3 only | Local, S3, Backblaze, Wasabi | **Minor gap** |

**Priority gaps:** Image transformations, direct file serving endpoint

### 4. Serverless Functions / Automation

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Visual Flow Designer | Yes (state machine, 15+ handlers) | No | **Kelta ahead** |
| Built-in Action Handlers | 15+ (CRUD, HTTP, email, notifications) | No (write your own) | **Kelta ahead** |
| Custom Code Execution | No (flows only) | Yes (15+ runtimes: Node, Python, Go, etc.) | **Gap** |
| Record Triggers | Yes | Yes (event triggers) | No |
| Scheduled Triggers | Stub (not wired) | Yes (cron) | **Gap** |
| HTTP/Webhook Triggers | Yes | Yes | No |
| Error Handling/Retry | Yes (retry + catch policies) | Basic | **Kelta ahead** |
| Durable Execution | Yes (persisted state machine) | No (stateless functions) | **Kelta ahead** |

**Priority gaps:** Scheduled trigger completion, custom code execution (scripting runtime)

### 5. Messaging / Notifications

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Email Delivery | Templates exist, no SMTP wired | Yes (Mailgun, SendGrid, SMTP) | **Gap** |
| SMS | No | Yes (Twilio, MSG91, etc.) | **Gap** |
| Push Notifications | No | Yes (APNs, FCM) | **Gap** |
| Topics/Channels | No | Yes | **Gap** |
| Message Scheduling | No | Yes | **Gap** |

**Priority gaps:** Email delivery integration (SMTP/SendGrid), SMS for auth

### 6. Realtime

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| WebSocket Subscriptions | No | Yes (multi-service) | **Gap** |
| Server-Sent Events | No | No | - |
| Permission-Aware Events | No | Yes | **Gap** |
| Internal Event Bus | Yes (Kafka) | Yes (internal) | No |

**Priority gaps:** WebSocket support for client-side realtime

### 7. Webhooks

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Outbound Webhooks | Yes (Svix) | Yes (native HMAC-SHA1) | No |
| Inbound Webhooks | Yes (flow triggers) | No | **Kelta ahead** |
| Signature Verification | Yes (Svix handles) | Yes (HMAC-SHA1) | No |

No significant gaps.

### 8. Developer Experience

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| REST API | Yes (JSON:API) | Yes | No |
| GraphQL | No | Yes | **Gap** |
| TypeScript SDK | Yes | Yes (Web + Node) | No |
| Multi-Language SDKs | TypeScript only | 14 SDKs (Python, Go, PHP, etc.) | **Gap** |
| CLI Tool | Type generator only | Full CLI | **Gap** |
| Admin Console UI | Yes (50+ pages) | Yes | No |
| API Documentation Site | No | Yes (auto-generated) | **Gap** |

**Priority gaps:** CLI tool, API docs site

### 9. Multi-Tenancy

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Tenant Isolation | Yes (per-tenant schemas + RLS) | Project-level only | **Kelta ahead** |
| Governor Limits | Yes (per-tenant configurable) | Basic rate limiting | **Kelta ahead** |
| Tenant Lifecycle Hooks | Yes (Svix, Superset, profiles) | No | **Kelta ahead** |
| Custom Domains | No | Yes | **Gap** |

**Priority gaps:** Custom tenant domains

### 10. Authorization / Permissions

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Policy Engine | Yes (Cerbos RBAC + ABAC) | Simple roles + labels | **Kelta ahead** |
| Object Permissions | Yes (per-collection CRUD) | Yes (collection-level) | No |
| Field Permissions | Yes (visible/read-only/hidden) | No | **Kelta ahead** |
| Document-Level Permissions | Via Cerbos rules | Yes (per-document roles) | Comparable |
| Authorization Testing | Yes (what-if UI) | No | **Kelta ahead** |

No significant gaps. Kelta is more advanced.

### 11. Analytics / Reporting

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Embedded Analytics | Yes (Superset) | No | **Kelta ahead** |
| Dashboards | Yes (via Superset) | No | **Kelta ahead** |
| Usage Statistics | Yes | Basic | **Kelta ahead** |

No gaps. Kelta is ahead.

### 12. Observability

| Feature | Kelta | Appwrite | Gap? |
|---------|-------|----------|------|
| Distributed Tracing | Yes (OpenTelemetry → Jaeger → OpenSearch) | No | **Kelta ahead** |
| Log Aggregation | Yes (OpenSearch) | Basic | **Kelta ahead** |
| Audit Trails | Yes (setup, security, login history) | Yes (basic) | **Kelta ahead** |
| Metrics Dashboard | Yes (7 pages, Prometheus) | No | **Kelta ahead** |
| Health Checks | Yes (K8s probes) | Yes (Health API) | No |

No gaps. Kelta is significantly ahead.

---

## Gap Analysis Summary

### Critical Gaps (High Impact, Competitors Expect This)

| # | Feature | Category | Effort | Why Critical |
|---|---------|----------|--------|-------------|
| 1 | **Email Delivery (SMTP/SendGrid)** | Messaging | Medium | Password resets, notifications, flow alerts are dead without it |
| 2 | **MFA / TOTP** | Auth | Medium | Enterprise security requirement |
| 3 | **Scheduled Flow Triggers** | Automation | Small | Cron parsing exists, just needs scheduler wiring |
| 4 | **API Keys / PATs** | Auth | Medium | Programmatic access, CI/CD integration |

### Important Gaps (Significant Value-Add)

| # | Feature | Category | Effort | Why Important |
|---|---------|----------|--------|-------------|
| 5 | **WebSocket Realtime** | Realtime | Large | Live updates, collaborative features |
| 6 | **Batch Operations** | Database | Medium | Bulk import/export, data migration |
| 7 | **Image Transformations** | Storage | Medium | Thumbnail generation, avatar resizing |
| 8 | **Direct File Serving** | Storage | Small | Stream files through API instead of presigned URLs |
| 9 | **Enhanced Password Policies** | Auth | Small | History, dictionary, complexity rules |
| 10 | **GraphQL API** | DX | Large | Alternative query interface |

### Nice-to-Have Gaps (Competitive Parity)

| # | Feature | Category | Effort | Notes |
|---|---------|----------|--------|-------|
| 11 | Multi-language SDKs | DX | Large | Python, Go, Java SDKs |
| 12 | CLI Tool | DX | Medium | Full CRUD, deployment, migration CLI |
| 13 | SMS Auth | Auth | Medium | Phone-based passwordless login |
| 14 | Push Notifications | Messaging | Medium | APNs + FCM integration |
| 15 | Custom Tenant Domains | Multi-tenancy | Medium | CNAME routing |
| 16 | Anonymous Sessions | Auth | Small | Guest access convertible to accounts |
| 17 | Offline Sync | Database | Large | Client-side offline data |
| 18 | API Documentation Site | DX | Medium | Auto-generated from schema |
| 19 | Magic URL / Email OTP | Auth | Small | Passwordless email login |
| 20 | Transactions | Database | Medium | Atomic multi-operation support |

---

## Recommended Roadmap Prioritization

### Phase 1: Foundation Gaps (Unblock Core Workflows)
1. Email delivery (SMTP/SendGrid) — unblocks password reset, flow alerts
2. Scheduled flow triggers — complete the stub
3. API keys / personal access tokens
4. Enhanced password policies

### Phase 2: Enterprise Security
5. MFA / TOTP authentication
6. Field-level security enforcement (strip HIDDEN fields from responses)
7. Direct file serving endpoint

### Phase 3: Developer Experience
8. Batch operations (bulk CRUD)
9. Image transformations
10. CLI tool improvements
11. API documentation site

### Phase 4: Realtime & Messaging
12. WebSocket realtime subscriptions
13. SMS authentication
14. Push notifications

### Phase 5: Advanced DX
15. GraphQL API
16. Multi-language SDKs
17. Custom tenant domains

---

## Kelta's Unique Strengths (Not in Appwrite)

These should be **preserved and highlighted** as competitive differentiators:

1. **Visual Flow Designer** with durable state machine execution
2. **Cerbos-backed RBAC/ABAC** with policy engine and authorization testing
3. **27 typed fields** including formula, rollup, currency, encrypted, auto-number
4. **Per-tenant schema isolation** with governor limits
5. **Comprehensive observability** (traces, logs, metrics, audit — 7 dashboard pages)
6. **Apache Superset analytics** embedding
7. **Field-level encryption** (AES-256-GCM per-tenant)
8. **Inbound webhook triggers** for flows
9. **Collection-level validation rules** with formula expressions
10. **Builder UI** (50+ admin pages) — Appwrite has console but not a builder

---

*Research completed: 2026-03-22*
*Sources: appwrite.io/docs, Kelta codebase exploration*
