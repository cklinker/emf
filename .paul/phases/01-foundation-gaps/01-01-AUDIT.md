# Enterprise Plan Audit Report

**Plan:** .paul/phases/01-foundation-gaps/01-01-PLAN.md
**Audited:** 2026-03-22
**Verdict:** Conditionally Acceptable — now upgraded to Enterprise-Ready after applying findings

---

## 1. Executive Verdict

The plan was **conditionally acceptable** before audit. The architecture is sound — standards-based SMTP, multi-tenant override pattern, SPI extensibility, and async delivery are all correct decisions. However, it had five gaps that would fail an enterprise audit: unauthenticated internal endpoint, credential leakage risk in logs/toString/errors, missing audit trail for SMTP routing decisions, no idempotency guarantee on password reset tokens, and insufficient test coverage for security paths.

After applying 4 must-have and 2 strongly-recommended upgrades, the plan is **enterprise-ready**. I would sign my name to this system.

## 2. What Is Solid (Do Not Change)

- **EmailProvider SPI design.** Single method, single record, single exception. Clean extension point. Correctly separates the "how to send" from "what to send" and "when to send."
- **Tenant settings in JSONB.** Reuses existing `tenant.settings` column. No schema migration for tenant config. Correct architectural decision — avoids a separate config table.
- **Async delivery via @Async.** Non-blocking email delivery is correct for this use case. ThreadPool with bounded queue prevents runaway resource consumption.
- **Caffeine cache for tenant JavaMailSender.** Avoids per-email connection setup. TTL-based eviction handles credential rotation.
- **Template resolution is already tenant-scoped.** V25 migration has `UNIQUE(tenant_id, name)` — no additional work needed.
- **Platform-then-tenant resolution order.** Correct fallback chain. Partial overrides (e.g., custom from address, platform SMTP) are well-specified.
- **Graceful failure pattern.** catch → log → update status → don't rethrow. Correct for email as a side-effect.

## 3. Enterprise Gaps Identified

1. **Internal endpoint has zero authentication.** InternalEmailController at `/api/internal/email/send` accepts requests from anyone who can reach port 80 on the worker. In Kubernetes, any pod in the namespace (or any pod with network access) can forge email sends. This is an email relay vulnerability.

2. **Tenant SMTP credentials in flight.** TenantEmailSettings is a Java record. Default `toString()` includes all fields including `smtpPassword`. Any log statement that logs the object (even accidentally via `logger.debug("settings: {}", tenantSettings)`) leaks credentials. Same risk in exception messages and error serialization.

3. **No audit trail for SMTP routing.** `email_log` records which tenant sent what to whom, but not *which SMTP server was used*. In a multi-tenant system where tenants bring their own SMTP, post-incident investigation ("was this email delivered via our server or the tenant's?") is impossible without this.

4. **Password reset token stacking.** PasswordController generates tokens but the plan doesn't specify invalidating prior tokens. A user who requests reset 3 times has 3 valid tokens, all usable within the expiry window. Only the latest should be valid.

5. **No test coverage for security-critical paths.** InternalEmailControllerTest only tested success/validation — not authentication rejection. TenantEmailSettingsTest verified parsing but the `toString()` masking test was noted but not connected to an AC.

## 4. Upgrades Applied to Plan

### Must-Have (Release-Blocking)

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 1 | Internal endpoint unauthenticated | Task 2 action (InternalEmailController), AC-9 added | Added X-Internal-Token header validation. Requests without valid token → 403. Must NOT be routable through gateway. |
| 2 | Credential leakage in TenantEmailSettings toString/logs/errors | Task 1 action (TenantEmailSettings), AC-8 added | Override toString() to mask smtpPassword. Password must not appear in logs, error messages, or serialized output. SmtpEmailProvider must not include password in EmailDeliveryException message. |
| 3 | Password reset token stacking | Task 3 action (PasswordController), AC-11 added | Invalidate previous reset tokens before generating new one (UPDATE SET reset_token = NULL). |
| 4 | Missing security test coverage | Task 3 tests | Added: InternalEmailController 403 tests (missing/invalid token), password reset idempotency test. |

### Strongly Recommended

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 5 | No SMTP routing audit trail | Task 2 action (new step 6), AC-10 added, files_modified, boundaries | Added smtpHost to EmailLogEntity. Populated in sendAsync() with resolved host. New migration V104. |
| 6 | Cache invalidation on auth failure | Task 1 action (SmtpEmailProvider) | Invalidate Caffeine cache entry if send fails with AuthenticationFailedException (tenant may have rotated credentials). |

### Deferred (Can Safely Defer)

| # | Finding | Rationale for Deferral |
|---|---------|----------------------|
| 1 | Rate limiting on internal email endpoint | Internal-only endpoint behind token auth. Rate limiting is a defense-in-depth measure, not a primary control. Can add in Phase 2 or when abuse patterns emerge. |
| 2 | Email content sanitization (XSS in HTML body) | Email bodies are generated by platform code (password reset, flow alerts), not user-supplied raw HTML. Template system in future may need sanitization, but current scope is safe. |
| 3 | Tenant SMTP credential encryption at rest in JSONB | Tenant settings JSONB is stored in PostgreSQL. Database-level encryption (TDE) is the correct layer for this. Application-level encryption of JSONB subfields adds complexity without clear benefit if DB encryption is enabled. |

## 5. Audit & Compliance Readiness

**Evidence produced:** email_log table with status transitions (QUEUED → SENDING → SENT/FAILED), source tracking (PASSWORD_RESET, WORKFLOW), smtp_host for routing audit, timestamps.

**Silent failure prevention:** Graceful failure pattern ensures every send attempt is logged regardless of outcome. No email can be attempted without a log record.

**Post-incident reconstruction:** Given an email complaint, an auditor can trace: tenant → email_log → smtp_host (platform or tenant) → source (which flow or system action) → timestamp → error (if failed). This is sufficient for SOC 2 evidence.

**Ownership:** Internal endpoint secured with shared secret. Caller identity (auth service) is traceable via source field. No anonymous email sends possible.

## 6. Final Release Bar

**Must be true before ship:**
- X-Internal-Token validation is enforced (not optional, not bypassable)
- TenantEmailSettings.toString() masks password (verified by unit test)
- email_log.smtp_host is populated on every send (verified by unit test)
- Password reset invalidates prior tokens (verified by unit test)

**Remaining risks if shipped as-is (post-audit):**
- Tenant SMTP credentials stored as plaintext in JSONB (deferred — DB encryption is the correct layer)
- No rate limiting on internal endpoint (deferred — token auth is primary control)
- Low: Email HTML body is platform-generated, not user-supplied, so XSS risk is minimal

**Sign-off:** After applying the 6 upgrades above, I would approve this plan for production. The credential handling, endpoint security, audit trail, and idempotency gaps have been addressed.

---

**Summary:** Applied 4 must-have + 2 strongly-recommended upgrades. Deferred 3 items.
**Plan status:** Updated and ready for APPLY

---
*Audit performed by PAUL Enterprise Audit Workflow*
*Audit template version: 1.0*
