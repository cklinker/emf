---
phase: 01-foundation-gaps
plan: 03
subsystem: auth
tags: [oauth2, client-credentials, jwt, connected-apps, api-keys, rfc6749]

requires: []
provides:
  - OAuth2 Client Credentials grant for connected apps
  - Connected app JWT tokens with tenant_id, connected_app_id, app_scopes claims
  - Token management (list, generate, revoke) via ConnectedAppTokenController
  - Near-instant revocation via Redis jti set
  - Connected app audit trail (V105 migration)
  - GatewayPrincipal.isConnectedApp() for machine identity detection
affects: [02-enterprise-security]

tech-stack:
  added: []
  patterns: [oauth2-client-credentials, redis-jti-revocation, connected-app-audit-trail, token-gen-rate-limiting]

key-files:
  created:
    - kelta-worker/src/main/java/io/kelta/controller/ConnectedAppTokenController.java
    - kelta-worker/src/main/java/io/kelta/repository/ConnectedAppRepository.java
    - kelta-worker/src/main/resources/db/migration/V105__add_connected_app_audit_table.sql
    - kelta-gateway/src/test/java/io/kelta/auth/GatewayPrincipalConnectedAppTest.java
    - kelta-worker/src/test/java/io/kelta/controller/ConnectedAppTokenControllerTest.java
  modified:
    - kelta-auth/src/main/java/io/kelta/auth/service/KeltaTokenCustomizer.java
    - kelta-gateway/src/main/java/io/kelta/auth/GatewayPrincipal.java
    - kelta-gateway/src/main/java/io/kelta/auth/PrincipalExtractor.java
    - kelta-web/packages/sdk/src/admin/AdminClient.ts
    - kelta-ui/app/src/pages/ConnectedAppsPage/ConnectedAppsPage.tsx
    - e2e-tests/tests/admin/integrations/connected-apps.spec.ts

key-decisions:
  - "OAuth2 Client Credentials (RFC 6749 §4.4) — no custom API key scheme"
  - "Same JWT pipeline for user and machine tokens — auth_method claim differentiates"
  - "Redis jti set for near-instant revocation (fail-open if Redis unavailable)"
  - "Token generation rate limited: 10 per 5 min per app"
  - "V105 connected_app_audit table for compliance tracking"

patterns-established:
  - "Machine identity via auth_method=api_key + connected_app_id in JWT claims"
  - "GatewayPrincipal.isConnectedApp() — single check for machine vs user"
  - "Token gen rate limit via Redis counter with TTL"
  - "Audit trail for security-sensitive operations (token gen/revoke, secret rotation)"

duration: ~30min
started: 2026-03-22T17:55:00Z
completed: 2026-03-22T18:18:00Z
---

# Phase 1 Plan 03: API Keys / Connected App Tokens Summary

**OAuth2 Client Credentials grant for connected apps with token management, audit trail, and near-instant revocation**

## Performance

| Metric | Value |
|--------|-------|
| Duration | ~30 min |
| Started | 2026-03-22T17:55Z |
| Completed | 2026-03-22T18:18Z |
| Tasks | 5 completed |
| Files created | 5 |
| Files modified | 7 |
| Tests added | ~15 unit + 4 e2e |
| PR | #582 (auto-merge) |

## Acceptance Criteria Results

| Criterion | Status | Notes |
|-----------|--------|-------|
| AC-1: Client Credentials Token Issuance | Pass | KeltaTokenCustomizer enriches client_credentials tokens |
| AC-2: Gateway Accepts Connected App Tokens | Pass | PrincipalExtractor detects auth_method=api_key |
| AC-3: Token Scoping | Pass | app_scopes claim extracted to GatewayPrincipal |
| AC-4: Token Revocation | Pass | Redis jti set + DB revoked flag |
| AC-5: Token Management UI | Pass | Tokens modal on ConnectedAppsPage |
| AC-6: Rate Limiting Per App | Pass | Redis counter, 10/5min default |
| AC-7: IP Restriction Enforcement | Partial | DB field exists, enforcement deferred to gateway filter integration |
| AC-8: Tenant Isolation | Pass | tenant_id in JWT claims, TenantContext set |
| AC-9: Audit Trail | Pass | connected_app_audit table, UI modal |
| AC-10: Token Revocation Propagation | Pass | Redis jti set with 1-hour TTL |
| AC-11: Secret Rotation Safety | Pass | Old tokens valid until expiry |
| AC-12: Token Generation Rate Limit | Pass | Redis counter, 429 on exceed |

## Accomplishments

- Connected apps can now authenticate via standard OAuth2 Client Credentials
- Same JWT pipeline validates both user and machine tokens — no separate auth path
- Token management UI with revocation and audit trail
- Near-instant revocation via Redis without waiting for JWT expiry
- Compliance-ready audit trail in dedicated table

## Task Commits

| Task | Commit | Type | Description |
|------|--------|------|-------------|
| All 5 tasks | `972505c` | feat | OAuth2 client credentials with token management |

## Decisions Made

| Decision | Rationale | Impact |
|----------|-----------|--------|
| OAuth2 Client Credentials, not custom API keys | Standards-first (RFC 6749 §4.4), reuses existing infra | No new auth pipeline needed |
| auth_method claim for differentiation | Single JWT pipeline for user + machine | Clean, auditable, maintainable |
| Redis jti set for revocation | JWTs are self-contained, can't revoke without external check | Near-instant revocation |
| Fail-open on Redis unavailable | Availability over strict security for transient Redis failures | Acceptable trade-off, logged |

## Deviations from Plan

### Summary

| Type | Count | Impact |
|------|-------|--------|
| Auto-fixed | 1 | Essential — fixed existing test |
| Partial | 1 | IP restriction enforcement deferred to gateway filter |
| Deferred | 0 | None |

### Auto-fixed Issues

**1. KeltaTokenCustomizerTest constructor change**
- **Found during:** Task 3 (tests)
- **Issue:** Test used no-arg constructor, but we added JdbcTemplate parameter
- **Fix:** Added @Mock JdbcTemplate to test, passed to constructor

### Partial Implementation

**IP Restriction Enforcement (AC-7):**
- DB field `ip_restrictions` exists on connected_app table
- ConnectedAppRepository reads the field
- Gateway enforcement filter not yet wired (requires integration with reactive filter chain)
- Will complete in Phase 2 (Enterprise Security) where gateway filter work is scoped

## Next Phase Readiness

**Ready:**
- OAuth2 Client Credentials flow operational
- Token management API + UI complete
- Audit trail for compliance
- GatewayPrincipal.isConnectedApp() available for downstream filters

**Concerns:**
- IP restriction enforcement not yet in gateway filters (AC-7 partial)
- Scope enforcement at collection level needs RouteAuthorizationFilter update
- Connected app OAuth2 client registration is startup-only (not dynamic on create)

**Blockers:**
- None

---
*Phase: 01-foundation-gaps, Plan: 03*
*Completed: 2026-03-22*
