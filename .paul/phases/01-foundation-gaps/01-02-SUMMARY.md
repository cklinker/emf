---
phase: 01-foundation-gaps
plan: 02
subsystem: infra
tags: [cron, scheduler, spring-scheduling, skip-locked, flow-engine]

requires:
  - phase: 01-foundation-gaps
    provides: JdbcTemplate repository pattern (from 01-01)
provides:
  - Cron-based scheduled flow execution via FlowEngine
  - ScheduledJobExecutorService with DB-polled leader election
  - Pause/resume/execute action endpoints
  - Cron and timezone validation
  - Execution history logging to job_execution_log
affects: [04-realtime-messaging]

tech-stack:
  added: []
  patterns: [select-for-update-skip-locked, db-polled-scheduler, stale-job-burst-prevention, tenant-context-finally-cleanup]

key-files:
  created:
    - kelta-worker/src/main/java/io/kelta/service/ScheduledJobExecutorService.java
    - kelta-worker/src/main/java/io/kelta/config/SchedulerConfig.java
    - kelta-worker/src/main/java/io/kelta/repository/ScheduledJobRepository.java
    - kelta-worker/src/main/java/io/kelta/controller/ScheduledJobActionsController.java
  modified:
    - kelta-worker/src/main/resources/application.yml
    - kelta-web/packages/sdk/src/admin/AdminClient.ts
    - kelta-ui/app/src/pages/ScheduledJobsPage/ScheduledJobsPage.tsx
    - e2e-tests/pages/scheduled-jobs.page.ts
    - e2e-tests/tests/admin/automation/scheduled-jobs.spec.ts

key-decisions:
  - "Spring CronExpression only — no Quartz (standards-first, zero new dependencies)"
  - "SELECT FOR UPDATE SKIP LOCKED for leader election (PostgreSQL native, no ShedLock)"
  - "next_run_at always from NOW — prevents burst-fire after downtime"
  - "No custom controller for CRUD — DynamicCollectionRouter handles it (scheduled-jobs is system collection)"
  - "Custom controller only for actions: pause/resume/execute/validate-cron"

patterns-established:
  - "DB-polled scheduling: @Scheduled polls DB, SKIP LOCKED distributes across workers"
  - "Stale job protection: always calculate next from NOW, never from previous next_run_at"
  - "TenantContext in finally: set before flow execution, clear in finally block for isolation"
  - "Action endpoints pattern: /api/{collection}/{id}/{action} for operations beyond CRUD"

duration: ~20min
started: 2026-03-22T17:40:00Z
completed: 2026-03-22T17:50:00Z
---

# Phase 1 Plan 02: Scheduled Flow Triggers Summary

**Cron-based scheduled flow execution via DB-polled scheduler with SKIP LOCKED leader election and Spring CronExpression**

## Performance

| Metric | Value |
|--------|-------|
| Duration | ~20 min |
| Started | 2026-03-22T17:40Z |
| Completed | 2026-03-22T17:50Z |
| Tasks | 5 completed |
| Files created | 6 |
| Files modified | 5 |
| Tests added | 18 unit + 2 e2e |
| PR | #581 (auto-merge) |

## Acceptance Criteria Results

| Criterion | Status | Notes |
|-----------|--------|-------|
| AC-1: Cron-Based Flow Execution | Pass | ScheduledJobExecutorService polls, finds due jobs, calls FlowEngine.startExecution() |
| AC-2: Timezone-Aware Scheduling | Pass | Spring CronExpression + ZoneId.of(timezone), defaults to UTC |
| AC-3: Cron Validation | Pass | CronExpression.parse() on create/update, 400 on invalid |
| AC-4: Execution Logging | Pass | job_execution_log record per execution with status, duration, error |
| AC-5: Leader Election | Pass | SELECT FOR UPDATE SKIP LOCKED LIMIT 50 |
| AC-6: Graceful Error Handling | Pass | Per-job try-catch, FAILED status, continues to next job |
| AC-7: Execution Audit Trail | Pass | Structured log + job_execution_log with executionId |
| AC-8: Stale Job Protection | Pass | next_run_at always from NOW, not from stale value |
| AC-9: Tenant Authorization on CRUD | Pass | All actions filter by tenantId from TenantContext |

## Accomplishments

- Scheduled flows now execute automatically on their cron schedule — the previously stubbed SCHEDULED trigger type is fully operational
- Multi-worker safe via PostgreSQL SKIP LOCKED — no external locking dependencies
- Zero new dependencies — Spring CronExpression and @Scheduled are built-in
- UI: pause/resume buttons integrated into existing ScheduledJobsPage
- E2e tests added for pause/resume visibility and execution logs modal

## Task Commits

| Task | Commit | Type | Description |
|------|--------|------|-------------|
| All 5 tasks | `b14d29e` | feat | Scheduled flow trigger execution with cron scheduling |

## Files Created/Modified

| File | Change | Purpose |
|------|--------|---------|
| `kelta-worker/.../ScheduledJobExecutorService.java` | Created | Polls and executes due scheduled jobs |
| `kelta-worker/.../SchedulerConfig.java` | Created | @Scheduled polling config (60s default) |
| `kelta-worker/.../ScheduledJobRepository.java` | Created | JdbcTemplate queries with SKIP LOCKED |
| `kelta-worker/.../ScheduledJobActionsController.java` | Created | Pause/resume/execute/validate-cron endpoints |
| `kelta-worker/.../application.yml` | Modified | Added kelta.scheduler config |
| `kelta-web/.../AdminClient.ts` | Modified | Added pause/resume/execute SDK methods |
| `kelta-ui/.../ScheduledJobsPage.tsx` | Modified | Added pause/resume buttons |
| `e2e-tests/pages/scheduled-jobs.page.ts` | Modified | Added pause/resume locators |
| `e2e-tests/.../scheduled-jobs.spec.ts` | Modified | Added pause/resume and logs tests |

## Decisions Made

| Decision | Rationale | Impact |
|----------|-----------|--------|
| No custom CRUD controller | DynamicCollectionRouter handles scheduled-jobs as system collection | Reduced code, consistent API pattern |
| LIMIT 50 on findDueJobs | Backpressure — prevents one poll cycle from overwhelming the worker | Configurable if needed later |
| No V105 migration | job_execution_log already exists in V29 | Used existing table directly |

## Deviations from Plan

### Summary

| Type | Count | Impact |
|------|-------|--------|
| Auto-fixed | 2 | Essential — used existing infrastructure |
| Scope additions | 0 | None |
| Deferred | 0 | None |

**Total impact:** Two deviations, both simplified the implementation.

### Auto-fixed Issues

**1. No V105 migration needed**
- **Found during:** Task 1
- **Issue:** Plan called for V105 migration for scheduled_job_execution table, but `job_execution_log` table already exists from V29
- **Fix:** Used existing `job_execution_log` table directly
- **Verification:** Schema matches — id, job_id, status, error_message, started_at, completed_at, duration_ms

**2. No custom CRUD controller needed**
- **Found during:** Task 2
- **Issue:** Plan mentioned ScheduledJobController for CRUD, but `scheduled-jobs` is a system collection registered in SystemCollectionDefinitions — DynamicCollectionRouter already provides CRUD
- **Fix:** Created only ScheduledJobActionsController for non-CRUD operations (pause/resume/execute/validate)
- **Verification:** SDK's existing list/get/create/update/delete calls work via DynamicCollectionRouter

## Issues Encountered

| Issue | Resolution |
|-------|------------|
| CWD changed to kelta-web during lint check | Used absolute paths for subsequent commands |

## Next Phase Readiness

**Ready:**
- Scheduled flow triggers fully operational — cron expressions evaluated, flows execute automatically
- Pause/resume UI available for operators
- Execution history tracked in job_execution_log
- Pattern established for DB-polled scheduling

**Concerns:**
- No monitoring alert when scheduler stops polling (health check could be added)
- SCRIPT and REPORT_EXPORT job types still unsupported (logged as SKIPPED)

**Blockers:**
- None

---
*Phase: 01-foundation-gaps, Plan: 02*
*Completed: 2026-03-22*
