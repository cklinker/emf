# Architecture

Kelta is a multi-tenant enterprise platform with configurable objects and workflows. All configuration is managed at runtime — no redeployment required.

## Services

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│   kelta-ui  │────▶│ kelta-gateway│────▶│  kelta-worker│
│  (React)    │     │ (API Gateway)│     │  (Services)  │
└─────────────┘     └──────┬───────┘     └──────┬───────┘
                           │                     │
                    ┌──────┴───────┐      ┌──────┴───────┐
                    │    Redis     │      │  PostgreSQL  │
                    │   (Cache)    │      │     (DB)     │
                    └──────────────┘      └──────────────┘
                           │
                    ┌──────┴───────┐
                    │    Kafka     │
                    │  (Events)    │
                    └──────────────┘
```

### kelta-gateway
Spring Cloud Gateway handling authentication, routing, and rate limiting. Acts as the single entry point for all API requests.

### kelta-worker
Primary service owning database migrations (Flyway), workflow execution, collection lifecycle, and all business logic. Manages the metadata-driven runtime.

### kelta-auth
Internal OIDC provider with identity brokering and MFA support. Handles user authentication, session management, and token issuance.

### kelta-ai
AI assistant service integrating with Anthropic Claude for intelligent platform interactions.

### kelta-platform/runtime
Shared runtime libraries:
- **runtime-core** — Collection management, query engine, validation, dual storage modes
- **runtime-events** — Shared Kafka event classes (`PlatformEvent<T>`)
- **runtime-jsonapi** — JSON:API response formatting
- **runtime-module-core** — CRUD action handlers

### kelta-web
Frontend SDK monorepo containing the SDK, component library, and plugin SDK.

### kelta-ui
Admin/builder UI built with React and Vite.

## Data Flow

1. Client requests hit **kelta-gateway** which authenticates and routes
2. API calls are forwarded to **kelta-worker** which processes business logic
3. Configuration changes are broadcast via **Kafka** so all pods stay in sync
4. **Redis** provides caching and session storage
5. **PostgreSQL** is the primary data store, managed by Flyway migrations

## Key Design Decisions

- **Multi-pod Kafka rule**: Configuration changes must be broadcast via Kafka events — never rely on in-process-only state changes
- **Metadata-driven**: Collections, fields, validations, and workflows are all runtime-configurable
- **Dual storage**: Supports both shared-table and dedicated-table modes per collection
