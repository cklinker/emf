# Kelta Platform

Kelta is a platform for building dynamic, runtime-configurable enterprise applications. It provides a metadata-driven architecture where collections (tables), fields, validation rules, relationships, and workflows are all defined and managed at runtime — no redeployment required.

## Architecture

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
       ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
       │  kelta-auth  │     │    Cerbos    │     │     NATS     │
       │   (OIDC)     │     │   (Authz)    │     │  (JetStream) │
       └──────────────┘     └──────────────┘     └──────────────┘
```

## Project Structure

| Module | Description |
|--------|-------------|
| `kelta-platform/runtime/runtime-core` | Core runtime library — collection management, query engine, validation, dual storage modes |
| `kelta-platform/runtime/runtime-events` | Shared NATS event classes for lifecycle events |
| `kelta-platform/runtime/runtime-jsonapi` | Shared JSON:API model classes |
| `kelta-platform/runtime/runtime-module-core` | Workflow action handlers (field updates, record CRUD, tasks, decisions) |
| `kelta-platform/runtime/runtime-module-integration` | Integration action handlers (HTTP callouts, email, scripts, delays) |
| `kelta-platform/runtime/runtime-module-schema` | Schema lifecycle hooks for system collections |
| `kelta-gateway` | Spring Cloud Gateway — authentication, authorization, JSON:API processing |
| `kelta-worker` | Worker service — owns database migrations, executes business logic |
| `kelta-web` | TypeScript SDK, React component library, and plugin SDK |
| `kelta-ui/app` | Admin/builder UI application |

## Tech Stack

**Backend:** Java 25, Spring Boot 4.x, Spring Cloud Gateway, Maven

**Frontend:** TypeScript, React 19, Vite, Vitest, Tailwind CSS

**Infrastructure:** PostgreSQL 15, Redis 7, NATS 2.10 (JetStream), Cerbos (authz). OIDC is
provided by the internal `kelta-auth` service — no external identity server required.
(Keycloak appears only in docker-compose as an optional federation IdP for testing.)

**Deployment:** Docker, Kubernetes, ArgoCD

## Prerequisites

- Java 25 (GraalVM Community 25.0.2; see `.tool-versions`)
- Maven 3.9+
- Node.js 18+
- Docker & Docker Compose

## Local Development

### One-command start

```bash
make setup   # first time only: copies .env, generates RSA JWK + AES key
make up      # starts postgres, redis, nats, cerbos, auth, worker, gateway, ui
make seed    # waits for healthy stack, then prints credentials
```

Default credentials (seeded by Flyway migrations):

| Field | Value |
|-------|-------|
| URL | http://localhost:5173 |
| Email | `admin@kelta.local` |
| Password | `password` (force-change on first login) |
| Tenant slug | `default` |

### Service ports

| Service | Host port | Notes |
|---------|-----------|-------|
| kelta-ui | :5173 | React admin UI |
| kelta-gateway | :8080 | Main API entry point |
| kelta-auth | :8081 | Internal OIDC provider |
| kelta-worker | :8083 | Business logic + Flyway |
| kelta-ai | :8084 | AI service (`--profile ai`) |
| Cerbos | :3592 (HTTP) / :3593 (gRPC) | Authorization engine |
| PostgreSQL | :5432 | |
| Redis | :6379 | |
| NATS | :4222 | |
| pgAdmin | :8092 | `--profile tools` |
| Redis Commander | :8091 | `--profile tools` |
| Mailpit (SMTP UI) | :8025 | `--profile tools` |

### Useful Makefile targets

```bash
make up-ai           # default stack + kelta-ai (needs ANTHROPIC_API_KEY in .env)
make up-full         # default + ai + tools
make up-telehealth   # default stack + LiveKit SFU (video visits, dev keys built in)
make rebuild SVC=kelta-worker   # rebuild + restart one service
make logs SVC=kelta-gateway     # tail logs
make down            # stop all containers
make reset           # wipe volumes and restart clean
```

### Debugging a service in the IDE (hybrid mode)

Stop the container for the service you want to debug, then launch it from IntelliJ using the checked-in run config in `.run/`:

```bash
make debug SVC=kelta-worker
# IntelliJ → Run → kelta-worker  (or use .run/kelta-worker.run.xml)
```

**One-time `/etc/hosts` entry required** (issuer URI consistency):

```
127.0.0.1  kelta-auth
```

This is necessary because `KELTA_AUTH_ISSUER_URI=http://kelta-auth:8080` is used
everywhere — inside Docker via container DNS and from the IDE via this hosts entry.
Without it, the gateway's issuer validation will reject tokens issued by
kelta-auth running in the IDE.

**Secrets in run configs** — the `.run/*.run.xml` files use `$VAR_NAME$` for
secrets (`KELTA_ENCRYPTION_KEY`, `JWK_SET`, `ANTHROPIC_API_KEY`). Set them in
the IntelliJ _Run/Debug Configurations → Environment variables_ dialog by
copying the values from your `.env` file.

### Build runtime libraries (required before first IDE run)

```bash
mvn clean install -DskipTests -f kelta-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B
```

## Running Tests

### Java

```bash
# Build runtime (required before testing)
mvn clean install -DskipTests -f kelta-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B

# Test gateway
mvn verify -f kelta-gateway/pom.xml -B

# Test worker
mvn verify -f kelta-worker/pom.xml -B
```

### Frontend

```bash
cd kelta-web
npm run lint
npm run typecheck
npm run format:check
npm run test:coverage
```

## Environment Variables

See [`.env.example`](.env.example). The only required secrets are generated by `make gen-keys`:

| Variable | Description |
|----------|-------------|
| `JWK_SET` | RSA-2048 JWK Set for JWT signing (generated by `make gen-keys`) |
| `KELTA_ENCRYPTION_KEY` | AES-256 key for envelope encryption (generated by `make gen-keys`) |
| `KELTA_INTERNAL_TOKEN` | Shared secret for internal service calls (default: `dev-internal-token`) |
| `ANTHROPIC_API_KEY` | Required only when running kelta-ai (`--profile ai`) |
| `KELTA_TELEHEALTH_VISIT_SECRET` | HMAC secret for emailed visit links (dev default with startup warning) |
| `KELTA_LIVEKIT_URL` / `KELTA_LIVEKIT_API_KEY` / `KELTA_LIVEKIT_API_SECRET` | LiveKit SFU for video visits (`--profile telehealth`; dev defaults match the compose service) |
| `MAXMIND_LICENSE_KEY` | Optional — enables the gateway's automatic GeoLite2 IP-geolocation downloads (free key from a MaxMind account). Without it geo enrichment is silently inactive; the mmdb persists in the `geoip-data` volume |

## CI/CD

Pull requests run the full quality gate via GitHub Actions:

1. **Build & Test Java** — builds runtime modules, runs tests for gateway, worker, auth, ai, mcp
2. **Test Frontend** — lint, typecheck, format check, and test coverage for kelta-web
3. **Integration & E2E** — Testcontainers harness + Playwright against the docker-compose stack
4. **Quality Gate** — all triggered jobs must pass before merge

On merge to `main`, container images are built and pushed to `harbor.rzware.com`, then
deployed to Kubernetes via ArgoCD (with smoke test + auto-rollback). Full detail:
[`.claude/docs/ci-cd.md`](.claude/docs/ci-cd.md).
