# Enterprise Plan Audit Report

**Plan:** .paul/phases/02-enterprise-security/02-01-PLAN.md
**Audited:** 2026-03-22
**Verdict:** Conditionally acceptable (now acceptable after upgrades applied)

---

## 1. Executive Verdict

The plan was **conditionally acceptable** before audit upgrades. The core TOTP architecture is sound — encrypted secrets, BCrypt-hashed recovery codes, per-tenant enforcement. However, the original plan had critical gaps in brute-force protection, replay prevention, and audit logging that would make MFA defeatable or unmonitorable in production.

After applying 4 must-have and 3 strongly-recommended upgrades, the plan is **enterprise-ready**. I would approve this for production.

## 2. What Is Solid

- **TOTP secret encryption via EncryptionService** — Correct use of existing AES-256-GCM infrastructure. Secrets at rest are not recoverable without the master key.
- **BCrypt-hashed recovery codes** — Recovery codes are shown once, then only BCrypt hashes stored. Correct approach.
- **Per-tenant MFA enforcement** — The `mfa_required` policy flag on password_policy is a clean extension of the existing per-tenant policy model.
- **MFA_PENDING session state** — Prevents partially-authenticated users from accessing the application. Correct separation between password auth and MFA auth.
- **Boundary constraints** — Correctly scoped to TOTP only, no scope creep into WebAuthn or SMS.
- **±1 window TOTP tolerance** — Standard practice for Google Authenticator compatibility.

## 3. Enterprise Gaps Identified

1. **No brute-force protection on MFA challenge** — 6-digit TOTP codes have 1,000,000 possibilities. At 100 attempts/second, exhaustive search takes ~2.8 hours. The plan has no rate limiting or lockout on the MFA challenge endpoint. An attacker with a stolen password could defeat MFA.

2. **No TOTP code replay prevention** — Within a 30-second TOTP window, the same code is valid for multiple submissions. An attacker who intercepts a code (shoulder surfing, MITM) can replay it. RFC 6238 Section 5.2 recommends tracking the last-used counter.

3. **No audit logging for MFA events** — The plan logs admin MFA resets but not MFA challenge attempts (success/failure), MFA enrollment, or MFA disable. This makes incident investigation impossible — you can't determine if MFA was brute-forced or if recovery codes were used without your knowledge.

4. **Recovery code entropy unspecified** — "xxxx-xxxx" format without specifying character set or randomness source. Could be implemented with Math.random() (predictable) or weak character sets.

5. **Session fixation after MFA** — After MFA verification changes the authentication state from MFA_PENDING to fully authenticated, the same session ID is used. An attacker who obtained the session ID during the MFA_PENDING phase retains access after MFA completes.

6. **No re-authentication for MFA changes** — Self-service MFA setup/disable only requires TOTP confirmation for disable. If an attacker hijacks an active session, they could enroll their own MFA (no password required) or disable existing MFA. Password re-entry is a standard control for security-critical self-service operations.

7. **Multi-tenant email collision** — `KeltaUserDetailsService.loadUserByUsername()` returns the first match when multiple users share an email across tenants. During MFA state loading, the wrong user's TOTP secret could be checked. This is an existing issue but MFA makes it security-critical.

## 4. Upgrades Applied to Plan

### Must-Have (Release-Blocking)

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 1 | MFA brute-force protection | AC (added AC-11), Task 1 (checkMfaRateLimit), V107 schema (mfa_failed_attempts, mfa_locked_until), Task 2 (rate limiting on POST endpoints) | Added rate limiting: 5 failed attempts → 5 min lockout, separate from password lockout |
| 2 | TOTP code replay prevention | AC (added AC-12), Task 1 (trackLastUsedCode), V107 schema (last_used_at), Task 1 test (replay test) | Track last-used TOTP epoch second per user, reject same code in same window |
| 3 | MFA event audit logging | AC (added AC-13), Task 2 (SecurityAuditLogger integration) | Log all MFA events: challenge success/failure, enrollment, disable, reset, recovery code use |
| 4 | Tenant-scoped MFA user lookup | AC (added AC-15), Task 2 (tenant-scoped resolution) | MFA state loaded using tenant context from login session, not first-match email lookup |

### Strongly Recommended

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 5 | Recovery code entropy | Task 1 (generateRecoveryCodes spec) | Specified SecureRandom, alphanumeric charset [a-z0-9], 8 chars per half (~41 bits entropy per code) |
| 6 | Session fixation prevention | Task 2 (step 6 added) | Regenerate session ID after MFA verification via request.changeSessionId() |
| 7 | Re-authentication for MFA changes | AC (added AC-14), Task 3 (self-service panel) | Password re-entry required before MFA enable/disable/regenerate |

### Deferred (Can Safely Defer)

| # | Finding | Rationale for Deferral |
|---|---------|----------------------|
| 1 | Trusted device management (remember this device) | Useful but not security-critical. Can be added as enhancement without architectural changes. |
| 2 | MFA enrollment email notification | Nice-to-have: email user when MFA is enrolled/disabled. Email infrastructure exists but notification templates not in scope. |
| 3 | KeltaUserDetailsService multi-tenant refactor | The tenant-scoped MFA lookup (AC-15) mitigates the immediate risk. Full refactor of loadUserByUsername to be tenant-aware is a larger change that should be addressed holistically. |

## 5. Audit & Compliance Readiness

**Audit evidence:** With AC-13 applied, all MFA events produce structured audit logs. An auditor can reconstruct: who enrolled MFA, when codes were used, failed attempts, admin resets. This meets SOC 2 CC6.1 (logical access) requirements.

**Silent failure prevention:** Rate limiting (AC-11) ensures brute-force attempts are detected and blocked. Replay prevention (AC-12) closes the window for code interception attacks.

**Post-incident reconstruction:** MFA event logs include actor, target, result, client IP, and timestamp. Combined with the existing auth failure logging (SecurityAuditFilter from Phase 1B), a complete attack timeline can be reconstructed.

**Ownership:** MFA is auth-service scoped. Admin management is worker-service scoped. Clean separation with audit trail bridging the two.

## 6. Final Release Bar

**Must be true before shipping:**
- All 15 acceptance criteria pass (original 10 + 5 audit-added)
- MFA challenge rate limiting verified by test (5 failures → lockout)
- TOTP replay prevention verified by test (same code rejected in same window)
- Recovery codes generated with SecureRandom (not Math.random)
- Session ID regenerated after MFA verification
- All MFA events logged via SecurityAuditLogger
- Self-service MFA changes require password re-entry

**Risks remaining if shipped as-is (post-audit):**
- Multi-tenant email collision in KeltaUserDetailsService — mitigated by tenant-scoped MFA lookup but root cause deferred
- No trusted device management — users must enter TOTP code every login
- No email notification when MFA is enabled/disabled — admin reset is logged but user not notified

**Sign-off:** With the applied upgrades, I would sign my name to this system. The critical attack vectors (brute-force, replay, session fixation) are addressed. The deferred items are genuine enhancements, not security gaps.

---

**Summary:** Applied 4 must-have + 3 strongly-recommended upgrades. Deferred 3 items.
**Plan status:** Updated and ready for APPLY

---
*Audit performed by PAUL Enterprise Audit Workflow*
*Audit template version: 1.0*
