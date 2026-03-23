# Enterprise Plan Audit Report

**Plan:** .paul/phases/02-enterprise-security/02-02-PLAN.md
**Audited:** 2026-03-22
**Verdict:** Conditionally acceptable (now acceptable after upgrades applied)

---

## 1. Executive Verdict

Well-scoped plan that addresses a genuine security gap. The read-side field security already works, and this plan correctly mirrors it for writes. The original plan had three gaps: no audit trail for stripped writes (compliance issue), no fail-closed behavior on Cerbos failure (security issue), and underspecified JSON:API body handling (implementation issue). After applying 2 must-have and 2 strongly-recommended upgrades, the plan is enterprise-ready.

## 2. What Is Solid

- **Silent stripping over rejection** — Correct choice. Returning 403 for individual fields leaks field visibility information. Stripping silently is consistent with the read side and doesn't reveal what the user can't see.
- **Reusing existing Cerbos infrastructure** — No new policies, no new tables, no new services. The `batchCheckFieldAccess(action="write")` API already exists.
- **System field bypass** — Correctly identified that system lifecycle fields should not be subject to Cerbos checks.
- **POST (create) exemption** — Reasonable for v1. New records need initial values, and field visibility is about protecting existing data, not creation.
- **Boundaries are tight** — Read-side advice untouched. No schema changes. No UI changes. Minimal blast radius.

## 3. Enterprise Gaps Identified

1. **No audit trail for silently stripped writes** — Silent stripping is correct behavior, but it must be observable. If a user reports "my field value disappeared after save", admins need to determine whether it was a security stripping or a bug. Without audit logging, this is invisible.

2. **Cerbos failure mode unspecified** — The read-side CerbosAuthorizationService has a fail-closed circuit breaker (returns empty list = deny all). The write-side advice must handle the same failure mode explicitly — if Cerbos is down, writes to non-system fields should be denied, not permitted.

3. **JSON:API body structure not specified** — The plan says "parse body as Map, extract attributes" but the actual body follows JSON:API format: `{"data":{"type":"...","attributes":{...}}}`. The advice must navigate this nested structure.

4. **POST exemption not fully justified** — While reasonable, a HIDDEN field on a POST create could be used to set privileged initial values. This is acceptable for now because the create path has its own validation, but should be documented as a known limitation.

## 4. Upgrades Applied to Plan

### Must-Have (Release-Blocking)

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 1 | Audit logging for stripped writes | AC (added AC-8), Task 1 action (security.audit log) | INFO-level log with event type, user, collection, field names |
| 2 | Fail-closed on Cerbos unavailability | AC (added AC-9), Task 1 action + tests | Strip all non-system fields when Cerbos unreachable |

### Strongly Recommended

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 3 | JSON:API body structure handling | AC (added AC-10), Task 1 action + tests | Navigate data.attributes structure, not flat Map |
| 4 | Test coverage for fail-closed and audit logging | Task 1 tests | Added 3 test scenarios |

### Deferred (Can Safely Defer)

| # | Finding | Rationale for Deferral |
|---|---------|----------------------|
| 1 | POST create field restrictions for HIDDEN fields | Create path has its own validation; field visibility is primarily about protecting existing data. Acceptable risk for v1. |

## 5. Audit & Compliance Readiness

With AC-8 applied, stripped writes produce audit evidence. An auditor can trace: which user attempted to write which fields, which were stripped, and why. This meets SOC 2 CC6.1 (logical access controls produce audit evidence).

Fail-closed behavior (AC-9) ensures that Cerbos outages don't create a window where field-level security is bypassed.

## 6. Final Release Bar

**Must be true:**
- Write-denied fields stripped silently from PUT/PATCH
- Security audit log emitted when fields are stripped
- Fail-closed when Cerbos unreachable
- JSON:API body structure correctly navigated
- All 10 acceptance criteria pass

**Remaining risks:** POST create exemption for HIDDEN fields. Acceptable.

**Sign-off:** With applied upgrades, I would approve this for production.

---

**Summary:** Applied 2 must-have + 2 strongly-recommended upgrades. Deferred 1 item.
**Plan status:** Updated and ready for APPLY

---
*Audit performed by PAUL Enterprise Audit Workflow*
*Audit template version: 1.0*
