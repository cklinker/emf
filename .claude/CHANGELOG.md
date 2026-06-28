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
- 2026-06-03 feat(runtime): filter orphan columns from JSON:API record responses — `DELETE /api/fields/{id}` only deprecates the underlying Postgres column (no DROP COLUMN), so `SELECT *` kept exposing deleted field names as `null` in `data.attributes`. `DynamicCollectionRouter.toJsonApiResourceObject` now drops keys that have no live `FieldDefinition`, no framework-metadata role (createdAt/updatedAt/createdBy/updatedBy/tenantId/recordTypeId), and no companion-column link to a still-live CURRENCY/GEOLOCATION field. Also corrects `McpApplicationTest.adminEndpointSurfaceCoversPhase6Through8` to include `delete_collection` added in #972 (TASK-2026-06-02-0005)

## 2026-06-08

- 2026-06-08 chore(ui): remove unused React imports and rename/mark unused locals across `kelta-ui/app` to clear all 86 TS6133 errors (CHORE-2026-06-08-0001)
- 2026-06-08 chore(ui): remove unused React imports and rename/mark unused locals across `kelta-ui/app` to clear all 86 TS6133 errors (CHORE-2026-06-08-0001)

## 2026-06-12

- 2026-06-12 chore(mcp): `create_flow` tool writes `flowType` (the real `flows` collection field) instead of the dead `triggerType` column; legacy `triggerType` argument is still accepted and remapped to `flowType` for back-compat (CHORE-2026-06-12-0001)
- 2026-06-12 feat(gateway): surface page[size] clamp in JSON:API metadata — `DynamicCollectionRouter.toJsonApiListResponse` now adds `metadata.requestedPageSize` and `metadata.pageSizeClamped=true` whenever the caller's `page[size]` exceeded `MAX_HTTP_PAGE_SIZE`, so an over-cap request no longer looks like a silent-empty-data response (TASK-2026-06-12-0004)
- 2026-06-12 feat(gateway): surface page[size] clamp in JSON:API metadata — `DynamicCollectionRouter.toJsonApiListResponse` now adds `metadata.requestedPageSize` and `metadata.pageSizeClamped=true` whenever the caller's `page[size]` exceeded `MAX_HTTP_PAGE_SIZE`, so an over-cap request no longer looks like a silent-empty-data response (TASK-2026-06-12-0004)
- 2026-06-12 feat(flow): surface swallowed Map iteration errors — `MapStateExecutor` now aggregates `{succeeded, failed, errors[capped@10], items}` into the Map result; sub-flow catches are recorded on `FlowExecutionContext`, propagated by `FlowEngine.executeSubFlow` via the new `SubFlowResult`, and the running `failedCount` is stamped onto the execution summary as `_failedCount`. New `MapState.FailOnPartial` (default false) flips a Map to `MapPartialFailure` when any iteration was caught, instead of silently passing through (TASK-2026-06-12-0005)
- 2026-06-12 feat(worker): expose scheduled-job status via `GET /api/flows/{id}/schedule` (cron, timezone, active, lastRunAt/lastStatus, nextRunAt) and `GET /api/flows/{id}/runs` (recent `job_execution_log` rows), plus a derived `scheduleStatus` that flags `UNSYNCED` when a SCHEDULED flow has no `scheduled_job` row or an active row with no `next_run_at` — so silently-unsynced flows (TASK-2026-06-12-0001 class of bug) no longer look healthy in the UI while never firing (TASK-2026-06-12-0003)
- 2026-06-12 feat(gateway): `?include=<field>` hydrates lookup FK fields — when the include name isn't a registered collection, `DynamicCollectionRouter.resolveIncludes` now falls back to resolving it as a field on the primary collection with a `referenceConfig`. The primary resource gets a `relationships.<field>.data = {type, id}` entry and the referenced row is pulled into `included[]`; the raw UUID stays on `attributes` for back-compat. Works for both LOOKUP-typed and legacy STRING-with-refConfig fields, with FK deduplication across rows for list responses (TASK-2026-06-12-0008)
- 2026-06-12 doc(flow): document the `{trigger,input,context}` initial state envelope and the `execute_flow` double-wrap rule (`body.input` ⇒ `state.input` ⇒ `$.input.<key>`); parallel `triggerConfig.inputData` convention for SCHEDULED flows. Tightened `ExecuteFlowTool` MCP description so callers see the wrap requirement before the misleading downstream error (DOC-2026-06-12-0001)
- 2026-06-12 feat(flow): add `InvokeFlow` state — a flow can synchronously call another flow in the same tenant by `FlowId`/`FlowName` with a templated `Input` map, merging the sub-flow result at `ResultPath`. `FlowStore` gains `findFlowDefinitionById/ByName`; `FlowEngine.executeInvokedFlow` increments a per-execution `invokeDepth` so direct/transitive recursion is bounded by `MAX_INVOKE_DEPTH=10` (`FlowDepthExceeded`). Sub-flow uncaught failures now propagate via `SubFlowResult.uncaughtError*` so the parent — and any Catch — can react. Works inside Map iterators (TASK-2026-06-12-0009)
- 2026-06-12 feat(flow): validate flow definition on save — new `FlowDefinitionValidator` (runtime-core) runs `FlowDefinitionParser` and a graph check (every `StartAt`/`Next`/`Default`/Choice target resolves to a defined state, `Map` iterators have a `StartAt`, transitions terminate). `FlowDefinitionValidationHook` (kelta-worker, order 50 — before `FlowConfigEventPublisher` and `FlowScheduleSyncHook`) calls the validator on flow create/update and rejects with a 400 listing every problem on the `definition` field, so malformed flows no longer save and only blow up at execution time (TASK-2026-06-12-0002)

## 2026-06-18

- 2026-06-18 feat(runtime): wire formula evaluation into `DefaultQueryEngine.computeVirtualFields()` — FORMULA fields now invoke `FormulaEvaluator.evaluate(expression, record)` on read (`getById`/`executeQuery`) using the `expression` key from `field_type_config`; missing/blank expression yields `null`, evaluation failures yield `"#ERROR"` so the UI always shows a live-computed value (TASK-2026-06-18-0004)
