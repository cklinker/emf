# Enterprise Plan Audit Report

**Plan:** .paul/phases/01-foundation-gaps/01-03-PLAN.md
**Audited:** 2026-03-22
**Verdict:** Conditionally Acceptable — upgraded to Enterprise-Ready after applying findings

---

## 1. Executive Verdict

The plan was **conditionally acceptable** before audit. The architecture is excellent — using OAuth2 Client Credentials (RFC 6749 §4.4) instead of a custom API key scheme is the right standards-based decision. Reusing the existing JWT validation pipeline in the gateway avoids a second authentication pathway and reduces attack surface.

However, three critical security gaps would cause production incidents in an enterprise deployment: JWT-only revocation (tokens valid until expiry), no token generation rate limiting (abuse vector), and credential logging risk. After applying 3 must-have and 2 strongly-recommended upgrades, the plan is **enterprise-ready**.

## 2. What Is Solid (Do Not Change)

- **OAuth2 Client Credentials grant.** Standards-compliant (RFC 6749 §4.4). No custom API key format to maintain. Tokens are JWTs validated by the same gateway infrastructure. Excellent decision.
- **Reusing JwtAuthenticationFilter.** One authentication pipeline for both user and machine tokens. Claims differentiate via `auth_method` — clean, maintainable, auditable.
- **Scope format: read:{collection}, write:{collection}, admin, full_access.** Fine-grained enough for API access control without being overly complex. Maps naturally to Cerbos permission model.
- **IP restriction enforcement.** Critical for enterprise — connected apps should be locked to known CIDR ranges. Database field already exists (V32), just needs enforcement.
- **Per-app rate limiting.** Uses existing Redis infrastructure. Configurable per connected app. Correct approach.
- **View-once token display pattern.** Already established in ConnectedAppsPage for client secrets. Extending to generated tokens is consistent.
- **No new dependencies.** Spring Authorization Server handles everything.

## 3. Enterprise Gaps Identified

1. **JWT revocation gap.** JWTs are self-contained — once issued, they're valid until expiry (default 1 hour). Revoking a token in the database has NO effect until the JWT naturally expires. In an enterprise scenario where a connected app's credentials are compromised, the attacker has up to 1 hour of access after revocation. This is a known JWT anti-pattern.

2. **Token generation abuse.** No rate limit on POST /oauth/token. An attacker with valid credentials could generate thousands of tokens per second, filling the connected_app_token table and consuming Redis/DB resources. More critically, each generated token is independently valid — revoking one doesn't revoke others.

3. **Credential logging risk.** The plan doesn't specify that client_secret values must never appear in logs. Given the existing codebase has request logging (RequestLoggingFilter), POST /oauth/token requests with client_secret in the body could be logged in plaintext.

4. **Token value in list endpoints.** If GET /api/connected-apps/{appId}/tokens returns the actual token string, any admin user can capture tokens. Tokens should be view-once on generation and never returned in list endpoints.

5. **Secret rotation and existing token validity.** When a client secret is rotated, the plan doesn't specify whether existing tokens (issued with the old secret) remain valid. In OAuth2, they should — the secret is used for token generation, not validation. But this needs to be explicit to prevent support confusion.

## 4. Upgrades Applied to Plan

### Must-Have (Release-Blocking)

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 1 | JWT revocation gap | Task 2 action (revocation check), AC-10 added | Redis-based revoked jti set. On revoke: add jti to set with TTL=remaining expiry. On auth: check jti in set before accepting. Fail-open if Redis unavailable. |
| 2 | Token generation abuse | Task 2 action (new step 6), AC-12 added | Redis counter per app: 10 tokens per 5-minute window. 429 on exceed. |
| 3 | Credential logging risk | Task 2 avoid section | Added: never log client_secret values, only client_id. |

### Strongly Recommended

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 4 | Token values in list endpoints | Task 2 avoid section | Added: token values never returned in list — view-once on generation only. |
| 5 | Secret rotation clarity | AC-11 added | Explicit AC: old secret rejected for new tokens, but existing JWTs valid until expiry. |

### Originally Deferred — Now Incorporated (per user request)

All three originally-deferred items have been added to the plan:

| # | Finding | Resolution |
|---|---------|------------|
| 1 | Token introspection endpoint (RFC 7662) | Added POST /oauth/introspect via Spring Authorization Server native support. Enables external resource servers to verify tokens. |
| 2 | Connected app audit log | Added V105 migration for `connected_app_audit` table. Tracks TOKEN_GENERATED, TOKEN_REVOKED, SECRET_ROTATED, APP_ACTIVATED, APP_DEACTIVATED, IP_RESTRICTIONS_CHANGED, SCOPES_CHANGED. UI audit trail section added. |
| 3 | Scope validation against collections | Added scope validation on create/update. Warns (non-blocking) on unrecognized collection names via CollectionRegistry. UI shows yellow warnings. |

## 5. Audit & Compliance Readiness

**Evidence produced:** Request logs with client_id attribution, connected_app.last_used_at timestamp, connected_app_token records with issued/revoked/expired states, Redis revocation set for near-instant enforcement.

**Silent failure prevention:** Fail-open on Redis unavailable (logs warning, accepts token) balances availability with security. Rate limiting prevents resource exhaustion. View-once tokens prevent credential leakage.

**Post-incident reconstruction:** Given a connected app compromise: revoke all tokens (jti added to Redis set) → tokens rejected within 60 seconds → audit trail shows which requests used the compromised tokens (client_id in request logs) → timeline reconstructable from connected_app_token.issued_at and request log timestamps.

**Ownership:** Connected apps are tenant-scoped. Token generation and revocation are attributed to the admin user who performs them. Machine requests are attributed via client_id in logs.

## 6. Final Release Bar

**Must be true before ship:**
- Revoked token jti is in Redis set and rejected by gateway within 60 seconds
- Token generation is rate-limited (10 per 5 min per app)
- Client secrets never appear in any log
- Token values never returned in list endpoints
- Existing tokens survive secret rotation

**Remaining risks:**
- Fail-open on Redis unavailable means revoked tokens may be accepted briefly if Redis is down (acceptable — Redis downtime is rare and transient)
- 1-hour token TTL means compromised tokens are valid for up to 1 hour even without Redis revocation (mitigated by TTL — consider reducing to 15 minutes for high-security deployments)

**Sign-off:** After applying the 5 upgrades, I would approve this plan for production.

---

**Summary:** Applied 3 must-have + 2 strongly-recommended upgrades. 3 originally-deferred items incorporated per user request.
**Plan status:** Updated and ready for APPLY

---
*Audit performed by PAUL Enterprise Audit Workflow*
*Audit template version: 1.0*
