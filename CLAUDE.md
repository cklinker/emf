# EMF Project — Claude Code Instructions

## Overview

This is the EMF Enterprise Platform monorepo. When working on tasks, follow this workflow exactly.

**Autonomy:** When a task is listed in an EPIC document, proceed through the entire workflow without asking for confirmation. Execute all steps (branch, implement, build, test, PR, auto-merge) end-to-end. Only stop to ask if there is a genuine ambiguity or blocker that cannot be resolved from the EPIC spec.

**All changes require a PR.** Never commit directly to `main`. Always create a feature branch, open a PR, and auto-merge. This applies to EPIC tasks, bug fixes, hotfixes, and any other code changes.

## Project Structure

```
emf-platform/runtime/runtime-core   # Core runtime library (Java, JAR)
emf-platform/runtime/runtime-events # Shared Kafka event classes (Java, JAR)
emf-gateway                         # Spring Cloud Gateway service (Java)
emf-worker                          # Worker service (Java, owns DB migrations)
emf-web                             # Frontend SDK, components, plugin-sdk (TypeScript/React)
emf-ui/app                          # Admin/builder UI (TypeScript/React/Vite)
```

- **Java 21**, Spring Boot 3.2.2, Maven
- **TypeScript/React**, Vite, Vitest, ESLint, Prettier
- **Database**: PostgreSQL with Flyway migrations
- **Messaging**: Kafka
- **Cache**: Redis

## Task Workflow

When assigned a task (e.g., "implement task A1"), follow these steps **in order**:

### 1. Look Up the Task

- Identify the task ID (e.g., `A1`, `B5`, `D3`) from the request.
- Determine which phase the task belongs to and read the corresponding EPIC document:
  - Phase 1 tasks → `EPIC-PHASE1.md`
  - Phase 2 tasks → `EPIC-PHASE2.md`
  - Future phases → `EPIC-PHASE<N>.md`

### 2. Create a Feature Branch

Branch from `main` using the task ID in the branch name:

```bash
git checkout main && git pull origin main
git checkout -b feature/<task-id>-<short-description>
```

**Branch naming convention:** `feature/<task-id-lowercase>-<kebab-case-description>`

Examples:
- `feature/a1-field-type-migration`
- `feature/b5-picklist-service`
- `feature/d3-formula-evaluator`

### 3. Implement the Feature

Read the full task specification from the relevant `EPIC-PHASE<N>.md` and implement:

- **Source code** — Follow existing patterns in the codebase.
- **Unit tests** — Test all new classes and methods.
- **Integration tests** — Update or add integration tests that verify the feature end-to-end.

#### Coding Standards

- Follow existing code style and patterns in the codebase.
- Java: No unused imports, no raw types, no unchecked casts.
- TypeScript: Must pass ESLint and Prettier checks.
- All new entities extend `BaseEntity` (UUID id, createdAt, updatedAt).
- All new JPA repositories extend `JpaRepository`.
- All new REST controllers follow existing `@RestController` patterns with proper `@RequestMapping`.
- Flyway migrations are numbered sequentially (see migration ranges below).
- Kafka events use `ConfigEventPublisher` pattern.

### 4. Build and Verify

Run the full build pipeline locally. **All steps must pass with zero errors.**

#### Java Backend

```bash
# 1. Build runtime modules (dependency for gateway and worker)
mvn clean install -DskipTests -f emf-platform/pom.xml -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema -am -B

# 2. Run gateway tests
mvn verify -f emf-gateway/pom.xml -B

# 3. Run worker tests
mvn verify -f emf-worker/pom.xml -B
```

#### Frontend (always — CI runs these on every PR)

```bash
# emf-web (always run — CI checks this on every PR regardless of changes)
cd emf-web && npm install
npm run lint
npm run typecheck
npm run format:check
npm run test:coverage
```

If changes were made to emf-ui:
```bash
cd emf-ui/app && npm install
npm run lint
npm run format:check
npm run test:run
```

#### Checklist Before PR

- [ ] `mvn verify` passes for gateway (zero test failures, zero lint errors)
- [ ] `mvn verify` passes for worker (zero test failures, zero lint errors)
- [ ] `npm run lint` passes in emf-web
- [ ] `npm run typecheck` passes in emf-web
- [ ] `npm run format:check` passes in emf-web
- [ ] `npm run test:coverage` passes in emf-web
- [ ] No compiler warnings introduced
- [ ] Flyway migration numbering is correct and sequential
- [ ] New tests cover the feature adequately

### 5. Commit and Push

```bash
git add <specific-files>
git commit -m "<type>(<scope>): <description>"
git push -u origin feature/<task-id>-<short-description>
```

**Commit message format:**
- `feat(runtime-core): extend FieldType enum with 16 new types`
- `feat(worker): add internal permissions endpoint`
- `feat(gateway): add permission enforcement filter`
- `fix(gateway): handle lookup field resolution in IncludeResolver`

### 6. Open a Pull Request and Auto-Merge

Create a PR using the GitHub CLI and enable auto-merge:

```bash
gh pr create \
  --title "[<TASK-ID>] <Short description>" \
  --body "$(cat <<'EOF'
## Summary
- <bullet points describing what was implemented>

## Task
- EPIC reference: EPIC-PHASE<N>.md, section <stream>

## Changes
- <list of key files added/modified>

## Testing
- <describe tests added>
- <describe how to verify manually if applicable>

## Checklist
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] No lint errors
- [ ] Flyway migration is correct
- [ ] Follows existing code patterns

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

# Enable auto-merge — PR will merge automatically once CI passes
gh pr merge --auto --squash
```

---

## Key Codebase Facts

| Fact | Value |
|------|-------|
| Flyway migration ranges | V1-V65 in emf-worker/src/main/resources/db/migration/ |
| FieldType enum (runtime-core) | STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME, JSON |
| FieldService VALID_FIELD_TYPES | "string", "number", "boolean", "date", "datetime", "reference", "array", "object" |
| Worker "number" field type maps to | runtime DOUBLE (not INTEGER) |
| BaseEntity fields | UUID string id (36 chars), createdAt, updatedAt |
| ConfigEventPublisher | Kafka-based, @Async, @ConditionalOnProperty |
| ReferenceConfig record | targetCollection, targetField, cascadeDelete |

## Reference Documents

- `EPIC-PHASE1.md` — Phase 1: Multi-Tenancy and Permission Foundation
- `EPIC-PHASE2.md` — Phase 2: Enhanced Object Model and Validation
- `TODO.md` — High-level implementation plan for all 6 phases
- `Specifications.MD` — Platform specifications

As new phases are planned, their EPIC documents will follow the pattern `EPIC-PHASE<N>.md`.

## CI/CD

- **PR checks** (`.github/workflows/ci.yml`): test-java (build runtime + test gateway, worker) + test-frontend → quality-gate
- **Deploy** (`.github/workflows/build-and-publish-containers.yml`): On main push, test-java + test-frontend → build-and-push (gateway, worker, UI images) → deploy → smoke-test
- All CI checks must pass before a PR can be merged
- **Deployment**: ArgoCD deploys to a local Kubernetes cluster. ArgoCD manifests are in a separate repo:
  - GitHub: `https://github.com/cklinker/homelab-argo`
  - Local path: `/Users/craigklinker/GitHub/homelab-argo`

## Kubernetes Access

The platform is deployed via ArgoCD to a local Kubernetes cluster. Use `kubectl` for debugging and log access.

| Resource | Value |
|----------|-------|
| Namespace | `emf` |
| Gateway | `deployment/emf-gateway` |
| Worker | `deployment/emf-worker` |

### Useful Commands

```bash
# View gateway logs
kubectl logs -n emf deployment/emf-gateway --tail=200

# View worker logs
kubectl logs -n emf deployment/emf-worker --tail=200

# Search for errors in last hour
kubectl logs -n emf deployment/emf-worker --since=1h | grep -i "ERROR\|exception"

# Check pod status
kubectl get pods -n emf

# Describe a pod for events/restarts
kubectl describe pod -n emf <pod-name>
```

## Branch and PR Policy

**All changes must go through a pull request.** Never push directly to `main`.

- **Every change** (features, bug fixes, hotfixes, config changes) must be made on a new branch.
- Create a PR for the branch and ensure CI passes.
- After CI passes, auto-merge the PR using `gh pr merge --auto --squash`.
- Branch naming: `feature/<description>` for features, `fix/<description>` for bug fixes.

### Auto-Merge Workflow

After creating a PR, enable auto-merge so it merges as soon as CI passes:

```bash
# Create PR and enable auto-merge in one step
gh pr create --title "<title>" --body "<body>"
gh pr merge --auto --squash
```

This replaces the previous workflow of waiting for manual merge approval. PRs will merge automatically once all required checks pass.
