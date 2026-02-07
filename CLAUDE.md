# EMF Project — Claude Code Instructions

## Overview

This is the EMF Enterprise Platform monorepo. When working on tasks, follow this workflow exactly.

## Project Structure

```
emf-platform/runtime/runtime-core   # Core runtime library (Java, JAR)
emf-platform/runtime/runtime-events # Shared Kafka event classes (Java, JAR)
emf-control-plane/app               # Control plane Spring Boot service (Java)
emf-gateway                         # Spring Cloud Gateway service (Java)
emf-web                             # Frontend SDK, components, plugin-sdk (TypeScript/React)
emf-ui/app                          # Admin/builder UI (TypeScript/React/Vite)
```

- **Java 21**, Spring Boot 3.2.2, Maven
- **TypeScript/React**, Vite, Vitest, ESLint, Prettier
- **Database**: PostgreSQL with Flyway migrations
- **Messaging**: Kafka
- **Cache**: Redis

## Plane Project Management

All task tracking is in Plane at https://plane.rzware.com.

### API Configuration

| Key | Value |
|-----|-------|
| API Base | `https://plane.rzware.com/api/v1/workspaces/emf` |
| API Key env var | `PLANE_API_KEY` |
| Project ID | `5e955d7a-4326-49ba-b384-e01d1ed76dea` |
| Auth header | `X-API-Key: <key>` |
| Rate limit | ~60 req/min, use 1s delay between calls |
| Redirects | Always use `-L` flag with curl |

### Plane States

| State | ID | Use When |
|-------|----|----------|
| Backlog | `80f2f9fa-57d6-488f-ad41-d037bc596562` | Default for new tasks |
| Todo | `197ada13-55a0-4e6a-ae1b-ac9a6c6ea228` | Planned but not started |
| In Progress | `40b07ee5-a419-433e-8ba9-f816c78926b4` | Work has begun |
| Code Review | `86edbb37-b817-4c10-9649-f3f6a28e5b2f` | PR opened, awaiting review |
| Done | `aa1c9c97-013d-4821-ad41-d037bc596562` | Merged and complete |
| Cancelled | `c794c6c0-374d-4d89-8e5f-0803142476f7` | Will not be done |

### Phase Labels

| Label | ID |
|-------|----|
| Phase 1 | `af0e5430-...` |
| Phase 2 | `ee15d1b7-b3c0-46ab-995a-2f1443d700c7` |
| Phase 3 | `36a15557-...` |
| Phase 4 | `cb10be47-...` |
| Phase 5 | `f639686d-...` |
| Phase 6 | `d5df2bdb-...` |

### Phase 2 Stream Labels

| Label | ID |
|-------|----|
| Field Types | `e08879e2-9409-4825-9f5e-b10aa3a1ea43` |
| Picklists | `953a2eae-92c8-4220-b349-12f89de5e883` |
| Relationships | `1e6c472f-c9cf-49a4-b31f-eaa8feaeb308` |
| Validation & Record Types | `0a33a8aa-b97a-4d24-804e-1bb1e8c1aa21` |
| Audit & History | `bf897318-a815-45a4-a65f-2c76d3d8b232` |

### Task ID → Plane Issue ID Mapping

Query the issue list from Plane to find the Plane issue ID for a given task ID:
```bash
curl -sL "https://plane.rzware.com/api/v1/workspaces/emf/projects/5e955d7a-4326-49ba-b384-e01d1ed76dea/issues/" \
  -H "X-API-Key: ${PLANE_API_KEY}" | python3 -c "
import sys, json
data = json.load(sys.stdin)
results = data.get('results', data) if isinstance(data, dict) else data
for issue in results:
    name = issue.get('name', '')
    if name.startswith('['):
        task_id = name.split(']')[0][1:]
        print(f'{task_id}: {issue[\"id\"]}')"
```

---

## Task Workflow

When assigned a task (e.g., "implement task A1"), follow these steps **in order**:

### 1. Look Up the Task

- Identify the task ID (e.g., `A1`, `B5`, `D3`) from the request.
- Determine which phase the task belongs to and read the corresponding EPIC document:
  - Phase 1 tasks → `EPIC-PHASE1.md`
  - Phase 2 tasks → `EPIC-PHASE2.md`
  - Future phases → `EPIC-PHASE<N>.md`
- Look up the Plane issue ID from the API (see "Task ID → Plane Issue ID Mapping" above).

### 2. Mark Task as In Progress

Set the Plane issue state to **In Progress** and record the start date:

```bash
ISSUE_ID="<plane-issue-id>"
START_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Update state to In Progress
curl -sL -X PATCH \
  "https://plane.rzware.com/api/v1/workspaces/emf/projects/5e955d7a-4326-49ba-b384-e01d1ed76dea/issues/${ISSUE_ID}/" \
  -H "X-API-Key: ${PLANE_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"state\": \"40b07ee5-a419-433e-8ba9-f816c78926b4\", \"start_date\": \"$(date -u +%Y-%m-%d)\"}"

# Add start comment
curl -sL -X POST \
  "https://plane.rzware.com/api/v1/workspaces/emf/projects/5e955d7a-4326-49ba-b384-e01d1ed76dea/issues/${ISSUE_ID}/comments/" \
  -H "X-API-Key: ${PLANE_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"comment_html\": \"<p><strong>Work started:</strong> ${START_DATE}</p>\"}"
```

### 3. Create a Feature Branch

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

### 4. Implement the Feature

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

### 5. Build and Verify

Run the full build pipeline locally. **All steps must pass with zero errors.**

#### Java Backend

```bash
# 1. Build runtime modules (dependency for control-plane and gateway)
mvn clean install -DskipTests -f emf-platform/pom.xml -pl runtime/runtime-core,runtime/runtime-events -am -B

# 2. Run control-plane tests
mvn verify -f emf-control-plane/pom.xml -B

# 3. Run gateway tests
mvn verify -f emf-gateway/pom.xml -B
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

- [ ] `mvn verify` passes for control-plane (zero test failures, zero lint errors)
- [ ] `mvn verify` passes for gateway (zero test failures, zero lint errors)
- [ ] `npm run lint` passes in emf-web
- [ ] `npm run typecheck` passes in emf-web
- [ ] `npm run format:check` passes in emf-web
- [ ] `npm run test:coverage` passes in emf-web
- [ ] No compiler warnings introduced
- [ ] Flyway migration numbering is correct and sequential
- [ ] New tests cover the feature adequately

### 6. Commit and Push

```bash
git add <specific-files>
git commit -m "<type>(<scope>): <description>"
git push -u origin feature/<task-id>-<short-description>
```

**Commit message format:**
- `feat(control-plane): add GlobalPicklist entity and repository`
- `feat(runtime-core): extend FieldType enum with 16 new types`
- `test(control-plane): add PicklistService unit tests`
- `fix(gateway): handle lookup field resolution in IncludeResolver`

### 7. Open a Pull Request

Create a PR using the GitHub CLI:

```bash
gh pr create \
  --title "[<TASK-ID>] <Short description>" \
  --body "$(cat <<'EOF'
## Summary
- <bullet points describing what was implemented>

## Task
- Plane task: [<TASK-ID>] <task name>
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
```

### 8. Mark Task as Code Review

After the PR is created, update the Plane issue:

```bash
ISSUE_ID="<plane-issue-id>"
PR_URL="<github-pr-url>"

# Update state to Code Review
curl -sL -X PATCH \
  "https://plane.rzware.com/api/v1/workspaces/emf/projects/5e955d7a-4326-49ba-b384-e01d1ed76dea/issues/${ISSUE_ID}/" \
  -H "X-API-Key: ${PLANE_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"state\": \"86edbb37-b817-4c10-9649-f3f6a28e5b2f\"}"

# Add implementation comment with PR link and details
curl -sL -X POST \
  "https://plane.rzware.com/api/v1/workspaces/emf/projects/5e955d7a-4326-49ba-b384-e01d1ed76dea/issues/${ISSUE_ID}/comments/" \
  -H "X-API-Key: ${PLANE_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "$(python3 -c "
import json
comment = '''<h3>Implementation Complete</h3>
<p><strong>PR:</strong> <a href=\"PR_URL\">PR_URL</a></p>
<p><strong>Branch:</strong> BRANCH_NAME</p>
<h4>What was implemented</h4>
<ul>
<li>SUMMARY_ITEM_1</li>
<li>SUMMARY_ITEM_2</li>
</ul>
<h4>Files changed</h4>
<ul>
<li>FILE_1</li>
<li>FILE_2</li>
</ul>
<h4>Tests added</h4>
<ul>
<li>TEST_1</li>
<li>TEST_2</li>
</ul>
<h4>Remaining work</h4>
<ul>
<li>REMAINING_ITEM (or 'None — feature is complete')</li>
</ul>
<p><strong>Completion date:</strong> COMPLETION_DATE</p>'''
print(json.dumps({'comment_html': comment}))
")"
```

Replace the placeholder values (PR_URL, BRANCH_NAME, SUMMARY_ITEM_*, FILE_*, TEST_*, REMAINING_ITEM, COMPLETION_DATE) with actual values.

### 9. After Merge (User Merges the PR)

Once the user confirms the PR is merged, mark the task as Done:

```bash
curl -sL -X PATCH \
  "https://plane.rzware.com/api/v1/workspaces/emf/projects/5e955d7a-4326-49ba-b384-e01d1ed76dea/issues/${ISSUE_ID}/" \
  -H "X-API-Key: ${PLANE_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"state\": \"aa1c9c97-013d-4821-90e3-51b71050de5f\"}"
```

---

## Key Codebase Facts

| Fact | Value |
|------|-------|
| Flyway migration ranges | V7 (base), V8–V13 (Phase 1), V14–V20 (Phase 2) |
| FieldType enum (runtime-core) | STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME, JSON |
| FieldService VALID_FIELD_TYPES | "string", "number", "boolean", "date", "datetime", "reference", "array", "object" |
| Control-plane "number" maps to | runtime DOUBLE (not INTEGER) |
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

- **PR checks** (`.github/workflows/ci.yml`): build-runtime → test-control-plane + test-gateway + test-frontend → build-check → quality-gate
- **Deploy** (`.github/workflows/build-and-publish-containers.yml`): On main push, builds Docker images, pushes to DockerHub, updates ArgoCD manifests in `homelab-argo` repo
- All CI checks must pass before a PR can be merged
