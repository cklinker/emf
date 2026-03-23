---
phase: 01-foundation-gaps
plan: 01
subsystem: infra
tags: [smtp, email, jakarta-mail, multi-tenant, spi]

requires: []
provides:
  - EmailProvider SPI for swappable email delivery
  - SmtpEmailProvider (SMTP default, per-tenant overrides)
  - DefaultEmailService implementing EmailService SPI
  - InternalEmailController for cross-service email
  - Password reset email delivery (previously TODO)
affects: [02-enterprise-security, 04-realtime-messaging]

tech-stack:
  added: [spring-boot-starter-mail, jakarta-mail]
  patterns: [EmailProvider SPI, tenant-settings-jsonb-override, caffeine-cached-tenant-senders, internal-token-auth]

key-files:
  created:
    - kelta-worker/src/main/java/io/kelta/service/email/EmailProvider.java
    - kelta-worker/src/main/java/io/kelta/service/email/SmtpEmailProvider.java
    - kelta-worker/src/main/java/io/kelta/service/email/DefaultEmailService.java
    - kelta-worker/src/main/java/io/kelta/service/email/TenantEmailSettings.java
    - kelta-worker/src/main/java/io/kelta/controller/InternalEmailController.java
    - kelta-worker/src/main/java/io/kelta/repository/EmailRepository.java
    - kelta-worker/src/main/java/io/kelta/config/EmailConfig.java
  modified:
    - kelta-worker/src/main/java/io/kelta/config/FlowConfig.java
    - kelta-auth/src/main/java/io/kelta/auth/controller/PasswordController.java
    - kelta-auth/src/main/java/io/kelta/auth/service/WorkerClient.java

key-decisions:
  - "SMTP only as default — no proprietary SDKs (standards-first principle)"
  - "Per-tenant SMTP overrides via tenant.settings JSONB — no new tables needed"
  - "JdbcTemplate repository (not JPA) — matches existing worker patterns"
  - "WorkerClient.sendEmail() for auth→worker — reuses existing RestClient pattern"
  - "X-Internal-Token header auth for internal endpoints"

patterns-established:
  - "EmailProvider SPI: single send() method with TenantEmailSettings for all future providers"
  - "Tenant override pattern: platform defaults + JSONB overrides, most-specific-wins"
  - "Internal endpoint security: X-Internal-Token header validation"
  - "Caffeine cache for tenant-specific JavaMailSender instances (5-min TTL, max 100)"

duration: ~25min
started: 2026-03-22T17:10:00Z
completed: 2026-03-22T17:22:00Z
---

# Phase 1 Plan 01: Email Delivery Summary

**Standards-based SMTP email delivery with per-tenant overrides, EmailProvider SPI, and password reset integration**

## Performance

| Metric | Value |
|--------|-------|
| Duration | ~25 min |
| Started | 2026-03-22T17:10Z |
| Completed | 2026-03-22T17:22Z |
| Tasks | 3 completed |
| Files created | 13 |
| Files modified | 6 |
| Tests added | 21 |
| PR | #580 (auto-merge) |

## Acceptance Criteria Results

| Criterion | Status | Notes |
|-----------|--------|-------|
| AC-1: Platform SMTP Defaults | Pass | spring.mail.* config drives default JavaMailSender |
| AC-2: Per-Tenant SMTP Override | Pass | TenantEmailSettings parsed from tenant.settings.email JSONB |
| AC-3: Per-Tenant Email Templates | Pass | EmailRepository queries by tenantId + templateId |
| AC-4: Provider SPI Extensibility | Pass | EmailProvider interface with single send() method |
| AC-5: Password Reset Email | Pass | PasswordController calls WorkerClient.sendEmail() |
| AC-6: Flow Email Alert Integration | Pass | DefaultEmailService wired into FlowConfig ModuleContext |
| AC-7: Graceful Failure | Pass | try-catch in sendAsync, FAILED status + error logged |
| AC-8: Tenant SMTP Credential Security | Pass | toString() masks password, not in exceptions |
| AC-9: Internal Endpoint Authorization | Pass | X-Internal-Token header validation, 403 on missing/invalid |
| AC-10: Email Log Audit Trail | Pass | smtp_host column added via V104, populated on send |
| AC-11: Idempotent Password Reset | Pass | Prior tokens invalidated before generating new one |

## Accomplishments

- EmailProvider SPI with SmtpEmailProvider — works out of the box with any SMTP server, extensible for custom providers
- Per-tenant SMTP overrides using existing tenant.settings JSONB — no new migration for tenant config
- Password reset emails now functional (previously a TODO stub since V25)
- Enterprise audit findings applied: internal endpoint auth, credential masking, token idempotency, smtp_host audit trail

## Task Commits

| Task | Commit | Type | Description |
|------|--------|------|-------------|
| All 3 tasks | `6a04119` | feat | Standards-based email delivery with per-tenant SMTP overrides |

## Files Created/Modified

| File | Change | Purpose |
|------|--------|---------|
| `kelta-worker/src/main/java/io/kelta/service/email/EmailProvider.java` | Created | SPI interface for email providers |
| `kelta-worker/src/main/java/io/kelta/service/email/SmtpEmailProvider.java` | Created | SMTP implementation with tenant sender cache |
| `kelta-worker/src/main/java/io/kelta/service/email/DefaultEmailService.java` | Created | Orchestrates templates, logging, async delivery |
| `kelta-worker/src/main/java/io/kelta/service/email/TenantEmailSettings.java` | Created | Parses tenant.settings.email JSONB |
| `kelta-worker/src/main/java/io/kelta/service/email/EmailMessage.java` | Created | Immutable email content record |
| `kelta-worker/src/main/java/io/kelta/service/email/EmailDeliveryException.java` | Created | Provider error wrapper |
| `kelta-worker/src/main/java/io/kelta/config/EmailConfig.java` | Created | Spring beans for email subsystem |
| `kelta-worker/src/main/java/io/kelta/controller/InternalEmailController.java` | Created | Token-secured internal endpoint |
| `kelta-worker/src/main/java/io/kelta/repository/EmailRepository.java` | Created | JdbcTemplate data access for email tables |
| `kelta-worker/src/main/resources/db/migration/V104__add_smtp_host_to_email_log.sql` | Created | Audit trail column |
| `kelta-worker/pom.xml` | Modified | Added spring-boot-starter-mail |
| `kelta-worker/src/main/java/io/kelta/config/FlowConfig.java` | Modified | Wired EmailService into ModuleContext |
| `kelta-worker/src/main/resources/application.yml` | Modified | SMTP + email config |
| `kelta-auth/src/main/java/io/kelta/auth/controller/PasswordController.java` | Modified | Replaced TODO with email delivery + token idempotency |
| `kelta-auth/src/main/java/io/kelta/auth/service/WorkerClient.java` | Modified | Added sendEmail() method |
| `kelta-auth/src/main/resources/application.yml` | Modified | Added internal token config |

## Decisions Made

| Decision | Rationale | Impact |
|----------|-----------|--------|
| JdbcTemplate instead of JPA entities | Worker uses JdbcTemplate exclusively — no JPA in codebase | Consistent with existing patterns |
| WorkerClient.sendEmail() instead of RestTemplate | Auth service already has WorkerClient with RestClient | Cleaner, reuses existing infrastructure |
| Caffeine cache for tenant senders (5m TTL, 100 max) | Avoids SMTP connection per email, auto-evicts on rotation | Performance + memory bounded |
| Invalidate cache on AuthenticationFailedException | Tenant may have rotated SMTP credentials | Self-healing on credential rotation |

## Deviations from Plan

### Summary

| Type | Count | Impact |
|------|-------|--------|
| Auto-fixed | 1 | Essential — matched existing patterns |
| Scope additions | 0 | None |
| Deferred | 0 | None |

**Total impact:** One deviation, essential for codebase consistency.

### Auto-fixed Issues

**1. Data Access Pattern — JdbcTemplate instead of JPA**
- **Found during:** Task 1 (JPA entities)
- **Issue:** Plan specified JPA entities + JpaRepository, but worker uses JdbcTemplate exclusively
- **Fix:** Created EmailRepository with JdbcTemplate queries instead of JPA entities
- **Files:** `kelta-worker/src/main/java/io/kelta/repository/EmailRepository.java`
- **Verification:** Compiles, tests pass, matches FlowRepository/BootstrapRepository patterns

## Issues Encountered

| Issue | Resolution |
|-------|------------|
| TenantContext.getTenantId() doesn't exist | Changed to TenantContext.get() — method is just `get()` |
| No RestTemplate bean in kelta-auth | Used existing WorkerClient with RestClient pattern instead |
| Worker files at io/kelta/ but package io.kelta.worker.* | Followed existing convention — directory flattened, packages prefixed |

## Next Phase Readiness

**Ready:**
- Email delivery infrastructure complete — flows, password resets, and future features can send email
- EmailProvider SPI available for Phase 4 (messaging) to add SMS/push providers
- Internal endpoint pattern established for future cross-service communication

**Concerns:**
- Tenant SMTP credentials stored as plaintext in JSONB (deferred — DB encryption is the correct layer)
- No email rate limiting (deferred from audit)

**Blockers:**
- None

---
*Phase: 01-foundation-gaps, Plan: 01*
*Completed: 2026-03-22*
