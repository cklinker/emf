# Kelta Platform changelog

This file tracks merged autopilot work. Entries are appended by autopilot workers as the final step of each task, one line per merged change, in the format `- YYYY-MM-DD <type>(<scope>): <one-line summary> (<task-id>)`. Newest dates appear at the bottom; group entries under an H2 date heading.

## 2026-05-10

- 2026-05-10 chore(autopilot): seed CHANGELOG.md (CHORE-2026-05-10-0001)

## 2026-05-11

- 2026-05-11 chore(repo): add .editorconfig with project-wide indentation rules (CHORE-2026-05-10-0004)
- 2026-05-11 doc(architecture): document the autopilot loop — topology, queue lifecycle, label gate, migration lock, bug ingress (DOC-2026-05-10-0001)
- 2026-05-11 chore(security): add SECURITY.md with vuln reporting policy and autopilot gating note (CHORE-2026-05-10-0002)
- 2026-05-11 chore(docs): add CONTRIBUTING.md outlining the autopilot workflow (CHORE-2026-05-10-0003)
- 2026-05-11 chore(autopilot): use task title+type for fallback commit message in worker.sh so `gh pr create --fill` produces a useful PR title (CHORE-2026-05-10-0005)
- 2026-05-11 doc(ui): add UI component consolidation plan covering DataTable/FilterBuilder/FieldRenderer/ResourceForm/RelatedList unification, feature superset, and migration order (DOC-2026-05-10-0002)
- 2026-05-11 chore(cache): wire NATS broadcast listeners on gateway and worker for `kelta.config.domain.changed.*` and `kelta.config.feature.changed.*` cache invalidation (CHORE-2026-05-10-0007)
- 2026-05-11 chore(events): publish `kelta.config.domain.changed.<id>` and `kelta.config.feature.changed.<tenantId>` from admin domain/governor-limits endpoints so all pods evict caches across the fleet (CHORE-2026-05-10-0006)
- 2026-05-11 chore(events): publish `kelta.config.domain.changed.<id>` and `kelta.config.feature.changed.<tenantId>` from admin domain/governor-limits endpoints so all pods evict caches across the fleet (CHORE-2026-05-10-0006)
- 2026-05-11 chore(test-harness): skip Testcontainers PG in `KeltaStack` when `$CI_DB_JDBC_URL` is set; use shared `kelta-ci-db` pool with per-run schema isolation (CHORE-2026-05-10-0008)
- 2026-05-11 fix(runtime): reconcile schema before issuing FK constraint statements in `PhysicalTableStorageAdapter.initializeCollection` so a NATS UPDATED event on a pod that fell into the initialize path no longer fails with "column does not exist" when the table already existed but the FK column was added later (BUG-2026-05-11-0001)

## 2026-05-30

- 2026-05-30 fix(runtime): swallow `DuplicateKeyException` on `pg_type_typname_nsp_index` from concurrent CREATE TABLE in `PhysicalTableStorageAdapter.initializeCollection` so the losing pod in a multi-pod NATS race continues to reconcile the winner's table instead of failing initialization (BUG-2026-05-30-2228)

## 2026-06-01

- 2026-06-01 fix(e2e): point E2E workflow + `.env.example` at `app.kelta.io`/`api.kelta.io`/`auth.kelta.io` after the rzware.com cutover; the legacy hostnames no longer serve a valid cert so Playwright was failing every test with `ERR_CERT_AUTHORITY_INVALID` (BUG-2026-06-01-4324)

## 2026-06-03

- 2026-06-03 chore(runtime): populate JSON:API error envelope on every 4xx response — `code` now mandatory; added `@ExceptionHandler`s for `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`, `NoHandlerFoundException`/`NoResourceFoundException`, `MethodNotAllowed`, `UnsupportedMediaType`, `ResponseStatusException`; fixed `JwtAuthenticationFilter` to emit `errors[]` array instead of singular `error` object (CHORE-2026-06-02-0008)
- 2026-06-03 chore(runtime): populate JSON:API error envelope on every 4xx response — `code` now mandatory; added `@ExceptionHandler`s for `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`, `NoHandlerFoundException`/`NoResourceFoundException`, `MethodNotAllowed`, `UnsupportedMediaType`, `ResponseStatusException`; fixed `JwtAuthenticationFilter` to emit `errors[]` array instead of singular `error` object (CHORE-2026-06-02-0008)
- 2026-06-03 chore(worker): enable email by default and point default SMTP at mailpit — `EMAIL_ENABLED` flipped to `true` in docker-compose, mailpit moved to the default profile, kelta-worker `application.yml` SMTP defaults now `localhost:1025` with auth/STARTTLS off so a bare `mvn spring-boot:run` reaches the dev mailpit (CHORE-2026-05-17-0003)
- 2026-06-03 feat(runtime): fix validation rule evaluator for OR/AND/NOT formulas + 422 response — `FormulaParser` (Java) and `kelta-web/packages/formula` (TS) now accept case-insensitive `AND`/`OR`/`NOT` keyword synonyms for `&&`/`||`/`!` with word-boundary checks so identifiers like `orderTotal`, `notes`, `andrew` are not consumed; `ValidationRuleDefinition` gains a `severity` field (defaults to `ERROR`) and `WARNING` rules log but never block; `GlobalExceptionHandler` maps `RecordValidationException` to HTTP 422 with code `VALIDATION_RULE_FAILED`; documented the formula grammar in `.claude/docs/conventions.md` (TASK-2026-06-02-0001)
- 2026-06-03 feat(worker): seed system email templates by `name` and add tenant-override fallback resolution — V141 inserts `password_reset`, `user_invite`, `welcome` templates under sentinel `tenant_id='system'`; `EmailRepository.findTemplateByName(tenantId, name)` mirrors `findTemplateByKey` semantics so a tenant row with the same `name` overrides the system default (TASK-2026-05-17-0001)
- 2026-06-03 feat(worker): add user invitation email flow — `POST /api/internal/email/invite` resolves the `user_invite` system template via `EmailRepository.findTemplateByName`, substitutes `${inviteLink}`/`${tenantName}`, and queues delivery via `DefaultEmailService.sendByName`; `WorkerClient.sendInviteEmail` (kelta-auth) fires it for newly JIT-provisioned federated users (TASK-2026-05-17-0002)
- 2026-06-03 chore(runtime): standardize pagination to JSON:API bracket syntax across all endpoints — `Pagination.fromParams` now clamps `page[size]` against new `MAX_HTTP_PAGE_SIZE=200` (separate from internal-only `MAX_PAGE_SIZE=1000`) so a runaway `?page[size]=500` caps at 200 instead of silently falling back to default; `DynamicCollectionRouter` list / sub-resource responses now carry a `links` block (`self` / `prev` / `next`, relative URLs preserving non-pagination params) built by new `io.kelta.jsonapi.PaginationLinks`; `ListPicklistsTool` MCP cap tightened from 500 → 200; OpenAPI generator documents the new bounds; conventions doc gets a REST API pagination section (CHORE-2026-06-02-0006)
