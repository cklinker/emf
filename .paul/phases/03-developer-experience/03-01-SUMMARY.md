---
phase: 03-developer-experience
plan: 01
subsystem: api
tags: [jsonapi, atomic-operations, bulk-crud, transactions, lid, batch]

requires:
  - phase: 01-foundation-gaps
    provides: QueryEngine CRUD infrastructure, CollectionRegistry

provides:
  - JSON:API Atomic Operations endpoint (POST /api/operations)
  - Transactional bulk create/update/delete (all-or-nothing)
  - Local ID (lid) resolution for server-generated IDs
  - Operation limit enforcement (default 100, ceiling 500)
  - AtomicOperationExecutor for sequential operation execution

affects: []

tech-stack:
  added: []
  patterns: [AtomicOperationExecutor with lid map, @Transactional batch endpoint]

key-files:
  created:
    - kelta-platform/runtime/runtime-jsonapi/src/main/java/io/kelta/jsonapi/AtomicOperation.java
    - kelta-platform/runtime/runtime-jsonapi/src/main/java/io/kelta/jsonapi/AtomicResult.java
    - kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/router/AtomicOperationExecutor.java
    - kelta-platform/runtime/runtime-core/src/test/java/io/kelta/runtime/router/AtomicOperationExecutorTest.java
    - kelta-worker/src/main/java/io/kelta/worker/controller/AtomicOperationsController.java
  modified: []

key-decisions:
  - "Executor is pure logic; controller owns @Transactional boundary"
  - "CollectionRegistry.get() not getByName() — matched existing API"
  - "ObjectMapper.convertValue for parsing operations from untyped Map"
  - "Security audit logging for batch success/failure"

patterns-established:
  - "AtomicOperationExecutor with lid→id Map for cross-operation references"
  - "@Transactional on controller method for batch atomicity"
  - "AtomicOperationException with operation index for error tracing"

duration: 20min
started: 2026-03-22T21:40:00Z
completed: 2026-03-22T22:00:00Z
---

# Phase 3 Plan 1: JSON:API Atomic Operations Summary

**Implemented JSON:API Atomic Operations extension with transactional bulk CRUD, lid resolution, and operation limits.**

## Performance

| Metric | Value |
|--------|-------|
| Duration | ~20 min |
| Started | 2026-03-22 |
| Completed | 2026-03-22 |
| Tasks | 2 completed |
| Files created | 5 |
| Lines added | 674 |

## Acceptance Criteria Results

| Criterion | Status | Notes |
|-----------|--------|-------|
| AC-1: Atomic Operations Endpoint | Pass | POST /api/operations with @Transactional |
| AC-2: Add (Create) Operations | Pass | Creates record, returns result with server id |
| AC-3: Update Operations | Pass | Updates by ref (type+id), returns result |
| AC-4: Remove Operations | Pass | Deletes by ref, returns empty result |
| AC-5: Local ID (lid) Resolution | Pass | lid→id map maintained across operations |
| AC-6: Transactional Atomicity | Pass | @Transactional rollback on any failure |
| AC-7: Operation Limit Enforcement | Pass | Default 100, hard ceiling 500 |
| AC-8: Per-Operation Authorization | Partial | Pre-auth framework in place, Cerbos integration deferred to runtime wiring |
| AC-9: Error Response Format | Pass | source.pointer with operation index |
| AC-10: Rate Limiting Counts Batch | Partial | Audit logging tracks count; gateway rate limiter integration deferred |
| AC-11: Kafka Events Per Operation | Pass | QueryEngine publishes events for each create/update/delete |
| AC-12: Idempotency Key | Deferred | Redis caching for idempotency not implemented in this plan |

## Accomplishments

- Full JSON:API Atomic Operations extension with 3 operation types
- 12 unit tests covering all operation types, validation, lid resolution, and error propagation
- Clean architecture: Executor (logic) + Controller (HTTP + transactions)
- Standards-compliant request/response format

## Task Commits

| Task | Commit | Type | Description |
|------|--------|------|-------------|
| Tasks 1-2 | `1039a4b0` | feat | Atomic Operations types, executor, controller |

PR: #590 (auto-merge enabled)

## Deviations from Plan

| Type | Count | Impact |
|------|-------|--------|
| Partial ACs | 3 | AC-8 (Cerbos pre-auth), AC-10 (rate limiter), AC-12 (idempotency) deferred |

**AC-8 (Pre-auth):** Framework in place but full Cerbos integration requires runtime wiring that depends on how the gateway forwards auth context. Will be wired when collection-level permissions are tested end-to-end.

**AC-10 (Rate limiting):** Gateway rate limiter counts HTTP requests, not batch operation counts. Requires gateway-side change to read a response header. Deferred.

**AC-12 (Idempotency):** Redis-based idempotency key caching is a separate concern. Can be added as middleware without changing the core batch logic.

## Issues Encountered

| Issue | Resolution |
|-------|------------|
| CollectionRegistry uses `get()` not `getByName()` | Fixed method name after compilation error |

## Next Phase Readiness

**Ready:**
- Plan 03-02 (Image transformations) is next
- Atomic operations foundation complete

**Concerns:**
- 3 audit-added ACs partially addressed — should be tracked

**Blockers:**
- None

---
*Phase: 03-developer-experience, Plan: 01*
*Completed: 2026-03-22*
