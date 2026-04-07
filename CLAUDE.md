# Kelta Enterprise Platform

Multi-tenant enterprise platform with configurable objects and workflows. All changes require a PR — never commit directly to `main`.

## Project Structure

```
kelta-platform/runtime/runtime-core   # Core runtime library (models, query, storage, flows)
kelta-platform/runtime/runtime-events # Shared event classes (PlatformEvent<T>)
kelta-platform/runtime/runtime-jsonapi # JSON:API response formatting
kelta-platform/runtime/runtime-module-core # CRUD action handlers
kelta-gateway                         # Spring Cloud Gateway (auth, routing, rate limiting)
kelta-auth                            # Internal OIDC provider, identity brokering, MFA
kelta-worker                          # Worker service (owns DB migrations, workflow exec)
kelta-ai                              # AI assistant service (Anthropic Claude integration)
kelta-web                             # Frontend SDK monorepo (sdk, components, plugin-sdk)
kelta-ui/app                          # Admin/builder UI (React/Vite)
e2e-tests                             # Playwright E2E tests
```

Stack: Java 25, Spring Boot 4.x, Maven, PostgreSQL + Flyway, NATS JetStream, Redis, React 19, Vite, Vitest. Check `kelta-platform/pom.xml` for current framework versions.

## Critical Rules

**Multi-pod NATS rule**: Never use in-process-only state changes for configuration data. Any change to in-memory registries or caches must be broadcast via NATS JetStream so all pods receive the update.

**Pattern**: When a system collection record changes and affects an in-memory registry:
1. Create a `BeforeSaveHook` for the system collection
2. In after-create/update/delete, publish via `PlatformEventPublisher` (e.g., to subject `kelta.config.collection.changed`)
3. All pods consume the event and refresh their local registries

Do NOT call `lifecycleManager.refreshX()` directly from a hook — that only updates the local pod.

**Coding patterns**:
- All new entities extend `BaseEntity` (UUID id, createdAt, updatedAt)
- All new JPA repositories extend `JpaRepository`
- Events use `ConfigEventPublisher` / `PlatformEventPublisher` pattern (NATS JetStream)
- Flyway migrations: check `kelta-worker/src/main/resources/db/migration/` for next sequential number

## Verification

Before any PR, run `/verify` to build and test all modules. CI runs the same checks. Use `/test-java` or `/test-frontend` for targeted testing.

## Reference Docs

Read these on demand when doing substantial work in the relevant area:

- `.claude/docs/architecture.md` — Service descriptions, layers, data flows, CI/CD, K8s access
- `.claude/docs/conventions.md` — Java and TypeScript naming, style, import order, error handling
- `.claude/docs/testing.md` — Test frameworks, structure, mocking patterns, coverage thresholds
- `.claude/docs/integrations.md` — External services (Cerbos, Svix, Superset, S3), data stores, NATS subjects, monitoring
- `.claude/docs/concerns.md` — Security risks, known bugs, tech debt, fragile areas, test gaps
- `.claude/docs/workflow.md` — Full task workflow, branch naming, PR template, pre-PR checklist
