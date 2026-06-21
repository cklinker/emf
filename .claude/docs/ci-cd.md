# CI/CD Pipeline

Source of truth: `.github/workflows/`. Verified against the workflow YAML — if a command
here disagrees with the YAML, the YAML wins.

## Shared environment (all workflows)

```
JAVA_VERSION=25          NODE_VERSION=18          MAVEN_VERSION=3.9.9
MAVEN_OPTS=-Dmaven.wagon.http.retryHandler.count=10 ...
```

- Maven cache key: `maven-${runner.os}-${hashFiles('**/pom.xml')}` over `~/.m2/repository`.
- npm cache key: `npm-${runner.os}-${hashFiles('kelta-web/package-lock.json')}` over `~/.npm`.
- Change detection: `.github/path-filters.yml` drives a `changes` job that outputs which
  services changed (`runtime`, `gateway`, `worker`, `auth`, `ai`, `mcp`, `web`, `ui`,
  `any_java`, `e2e`, `workflows`). Downstream jobs run only for changed paths; a
  `quality-gate` job passes if every *triggered* job passed (skipped jobs are allowed).

## `ci.yml` — Pull-request CI

Trigger: `pull_request` → `main`, plus `workflow_dispatch`.

| Job | What it does |
|-----|--------------|
| `changes` | Path-filter detection (above). |
| `test-java` | Matrix `[gateway, worker, auth, ai, mcp]`. Builds runtime libs, then `mvn verify -f kelta-<svc>/pom.xml -B`. Uploads `kelta-test-results-<svc>` (surefire XML) + `java-coverage-<svc>` (JaCoCo). |
| `test-frontend` | In `kelta-web`: `npm ci`, `npm run lint`, `npm run typecheck`, `npm run format:check`, `npm run test:coverage` (Vitest, v8 coverage, **80% threshold** in `vitest.config.ts`). |
| `integration-tests` | Builds service JARs, pre-pulls images (`redis:7`, `nats:2.10`, `cerbos:0.40.0`, `eclipse-temurin:25-jre`), pre-builds service images, then `mvn verify -f kelta-test-harness/pom.xml -Pintegration-tests` (failsafe). `TESTCONTAINERS_RYUK_DISABLED=true`. |
| `e2e` | Builds JVM service images (`Dockerfile.jvm`), spins up the full stack via `docker-compose.yml -f docker-compose.ci.yml`, runs Playwright (`mcr.microsoft.com/playwright:v1.58.2-noble`) inside the compose network. Timeout 45 min. Uploads HTML report + traces. |
| `quality-gate` | Green iff all triggered jobs passed. |

## `build-and-publish-containers.yml` — Post-merge deploy

Trigger: `push` → `main` (path-filtered), plus `workflow_dispatch`.

1. `changes`, `test-java`, `test-frontend` — same as CI.
2. **`build-and-push`** — matrix `[gateway, worker, worker-migrate, auth, ui, ai, mcp]`.
   Docker buildx → pushes to `harbor.rzware.com/emf/emf-<svc>:latest` and
   `:main-<short-sha>`. Per-service GHA cache scope.
3. **`deploy`** — checks out `homelab-argo`, runs kustomize to bump image tags (verifies
   each image exists on Harbor first), commits to `homelab-argo`. ArgoCD then syncs.
4. **`smoke-test`** — waits for k8s rollouts; `curl .../actuator/health/liveness` on gateway
   + worker.
5. **`rollback-on-smoke-failure`** — `git revert HEAD` in `homelab-argo` if smoke fails.
6. **`e2e-test`** — Playwright against production (`app.kelta.io`, `api.kelta.io`) using
   E2E token + Authentik secrets. Timeout 25 min. Files failing-E2E bug tasks to `emf-queue`.

## `auto-merge.yml`

Auto-merges PRs labeled `autopilot` from authorized actors (`cklinker`,
`github-actions[bot]`). Squash strategy. Uses `ARGOCD_REPO_TOKEN` (a PAT, so the merge
push triggers the downstream publish workflow). **`type: security` tasks are never
auto-merged** (see `SECURITY.md`).

## Other workflows

- `build-runner-image.yml` — builds the self-hosted CI runner image.
- `.github/workflows/README.md` and `scripts/ci/README.md` — runner + shared CI DB
  (`kelta-ci-db`, schema-isolated per run) notes.

## Container registry & deploy

- Registry: `harbor.rzware.com/emf/emf-<service>`.
- Manifests: `homelab-argo` (kustomize), synced by **ArgoCD** to the local K8s cluster,
  namespace **`kelta`**. In-cluster service DNS is `emf-<service>` (e.g. `emf-gateway`).
- Local dev never touches CI: `make up` / `docker-compose.yml`. CI overrides live in
  `docker-compose.ci.yml` (no fixed host ports, JVM Dockerfiles, CI-only cerbos image).
