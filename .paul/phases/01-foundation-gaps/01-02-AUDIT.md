# Enterprise Plan Audit Report

**Plan:** .paul/phases/01-foundation-gaps/01-02-PLAN.md
**Audited:** 2026-03-22
**Verdict:** Conditionally Acceptable — upgraded to Enterprise-Ready after applying findings

---

## 1. Executive Verdict

The plan was **conditionally acceptable** before audit. The core architecture is correct — DB polling with SKIP LOCKED is the right pattern for stateless multi-worker scheduling without adding Quartz. Spring CronExpression is standards-compliant. InitialStateBuilder.buildFromSchedule() already exists, reducing implementation risk.

However, four gaps would cause production incidents: stale job burst-fire after downtime, missing tenant isolation on CRUD, TenantContext leak between jobs, and no timezone validation. After applying 3 must-have and 3 strongly-recommended upgrades, the plan is **enterprise-ready**.

## 2. What Is Solid (Do Not Change)

- **SELECT FOR UPDATE SKIP LOCKED.** Correct leader election pattern for PostgreSQL. Avoids distributed locking complexity. Works across multiple worker pods. Lock released on transaction commit.
- **DB-polled scheduling.** Stateless workers — no in-memory job state that dies with the process. Correct for Kubernetes where pods restart frequently.
- **Spring CronExpression only.** No Quartz dependency. Standards-first principle upheld.
- **FlowEngine.startExecution() is already async.** The scheduler thread won't block during flow execution. Correct separation of scheduling from execution.
- **Per-job error isolation.** Each job in its own try-catch, failures don't block subsequent jobs. Correct pattern.

## 3. Enterprise Gaps Identified

1. **Stale job burst-fire.** If a worker is down for 6 hours, a job with `cron="*/5 * * * *"` has next_run_at 6 hours in the past. If next_run_at is recalculated from the stale value, the job fires 72 times in rapid succession. The plan didn't specify whether next_run_at is calculated from NOW or from the stale value.

2. **Cross-tenant data access on CRUD.** The controller plan listed `GET /{id}` and `DELETE /{id}` but didn't specify tenant scoping. Without `WHERE tenant_id = ?`, a user could read/modify another tenant's scheduled jobs by guessing the UUID.

3. **TenantContext leak between jobs.** The plan mentions "Clear TenantContext after each job" but doesn't specify it must be in a `finally` block. If a job throws an unchecked exception before clear(), the next job runs with the wrong tenant context — a security isolation breach.

4. **No timezone validation.** The plan validates cron expressions but not timezone strings. `ZoneId.of("Invalid/Zone")` throws DateTimeException, which would surface as a 500 instead of a 400.

5. **No execution audit trail.** The plan updates last_status but doesn't log the executionId returned by FlowEngine. Without this, there's no link between a scheduled job execution and the flow execution trace. Post-incident reconstruction requires both.

6. **next_run_at recalculation on non-FLOW types and failed lookups.** The plan skips unsupported job types and missing flows but doesn't recalculate next_run_at for them. These jobs would be picked up again on every poll cycle, wasting DB resources.

## 4. Upgrades Applied to Plan

### Must-Have (Release-Blocking)

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 1 | Stale job burst-fire | Task 2 action (executeAll), AC-8 added | next_run_at always calculated from NOW. Added explicit note: stale jobs fire once, then schedule forward from current time. |
| 2 | Cross-tenant CRUD access | Task 2 action (controller), AC-9 added | All queries filter by tenantId from TenantContext. GET /{id} returns 404 (not 403) for other tenant's jobs. |
| 3 | TenantContext leak | Task 2 action (executeAll step d) | Specified "in finally block". Added test: verify clear() called even on exception. |

### Strongly Recommended

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 4 | Timezone validation | Task 2 action (controller) | Added ZoneId.of() validation on create/update. Invalid → 400. |
| 5 | Execution audit trail | Task 2 action (executeAll step b), AC-7 added | Log jobId, flowId, tenantId, executionId, status, duration after each execution. |
| 6 | Recalculate next_run_at on skip/fail | Task 2 action (executeAll steps b) | Added "recalculate next_run_at" to skip and fail paths for unsupported types and missing flows. |

### Originally Deferred — Now Incorporated (per user request)

All three originally-deferred items have been added to the plan:

| # | Finding | Resolution |
|---|---------|------------|
| 1 | Execution history table | Added V105 migration for `scheduled_job_execution` table with jobId, executionId, status, duration, error. Added `GET /{id}/executions` endpoint. |
| 2 | Pause/resume endpoints | Added `POST /{id}/pause` (clear next_run_at) and `POST /{id}/resume` (recalculate from NOW). |
| 3 | Backpressure on due jobs | Added `LIMIT 50` to findDueJobs() query. |

## 5. Audit & Compliance Readiness

**Evidence produced:** scheduled_jobs.last_run_at, last_status, next_run_at provide execution history. Structured log with jobId+executionId links to flow_execution trace. Tenant-scoped queries prevent data leakage.

**Silent failure prevention:** Per-job try-catch with status update ensures every execution attempt is recorded. next_run_at recalculated even on failure ensures jobs aren't permanently stuck.

**Post-incident reconstruction:** Given a scheduled job concern, trace: scheduled_job.id → log (jobId, executionId) → flow_execution (full step trace). Sufficient for SOC 2.

**Ownership:** Jobs are tenant-scoped. Execution is attributed via tenantId in logs and flow_execution. No anonymous scheduled execution possible.

## 6. Final Release Bar

**Must be true before ship:**
- TenantContext.clear() in finally block for every job execution
- next_run_at calculated from NOW (not stale value)
- All CRUD queries include `WHERE tenant_id = ?`
- Invalid timezone returns 400 (tested)

**Remaining risks:**
- No backpressure on large batches of due jobs (deferred — SKIP LOCKED distributes naturally)
- No dedicated scheduled_job_execution history table (deferred — flow_execution + logs sufficient)

**Sign-off:** After applying the 6 upgrades, I would approve this plan for production.

---

**Summary:** Applied 3 must-have + 3 strongly-recommended upgrades. 3 originally-deferred items incorporated per user request.
**Plan status:** Updated and ready for APPLY

---
*Audit performed by PAUL Enterprise Audit Workflow*
*Audit template version: 1.0*
