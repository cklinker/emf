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
- 2026-06-03 feat(runtime): composite UNIQUE constraint support — `POST/GET/DELETE /api/_composite-unique-constraints` + `create_unique_constraint` MCP admin tool; multi-column duplicate inserts return 409 with every constrained field named (TASK-2026-06-02-0007)
