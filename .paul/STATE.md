# Project State

## Project Reference

See: .paul/PROJECT.md (updated 2026-03-22)

**Core value:** Teams can build and manage business applications through a multi-tenant enterprise platform with configurable objects, fields, permissions, and workflows — without custom development.
**Current focus:** Phase 1B — Security Hardening

## Current Position

Milestone: v1.0 Competitive Parity
Phase: 1B of 7 (Security Hardening) — Planning
Plan: 01b-01 created, awaiting approval
Status: Ready for APPLY
Last activity: 2026-03-22 — Phase 1A complete, transitioned to Phase 1B

Progress:
- Milestone: [███░░░░░░░] 28%
- Phase 1: [██████████] 100% — Complete
- Phase 1A: [██████████] 100% — Complete
- Phase 1B: [░░░░░░░░░░] 0% (2 plans, ready)

## Loop Position

Current loop state:
```
PLAN ──▶ APPLY ──▶ UNIFY
  ✓        ○        ○     [Plan 01b-01 created, ready for APPLY]
```

## Performance Metrics

**Velocity:**
- Total plans completed: 5
- Average duration: 22 min
- Total execution time: ~1.8 hours

**By Phase:**

| Phase | Plans | Total Time | Avg/Plan |
|-------|-------|------------|----------|
| 01-foundation-gaps | 4/4 | ~100 min | 25 min |
| 01a-namespace-alignment | 1/1 | ~10 min | 10 min |
| 01b-security-hardening | 0/2 | - | - |

## Accumulated Context

### Codebase Mapped
Date: 2026-03-22
Documents: .paul/codebase/ (7 files)

### Research Completed
- .paul/research/appwrite-vs-kelta.md
- .paul/research/strapi-vs-kelta.md
- .paul/research/jsonapi-atomic-operations.md

### Security Audit Completed
Date: 2026-03-22
Findings: 12 items (5 high, 3 medium, 4 infra recommendations)
Plans created: 01b-01 (code fixes), 01b-02 (CI + infra docs)

### Decisions
- GraphQL API excluded from roadmap (user decision)
- Batch operations to follow JSON:API Atomic Operations extension
- Standards-first: SMTP, OAuth2 Client Credentials, Spring CronExpression — open source only
- Per-tenant SMTP overrides stored in tenant.settings JSONB
- JdbcTemplate repos for worker code — matches existing patterns
- SELECT FOR UPDATE SKIP LOCKED for scheduler leader election
- OAuth2 Client Credentials (RFC 6749 §4.4) for connected apps — no custom API keys
- Redis jti set for near-instant token revocation
- Token generation rate limited: 10 per 5 min per app
- All features require full UI + unit tests + e2e tests
- NIST SP 800-63B password defaults (length > complexity, no arbitrary rotation)
- Bundled 10k dictionary (no HIBP API) — open source only
- Timing-safe password history comparison (always compare all N entries)
- Namespace alignment: single PR, all 509 files (user decision)
- Security audit: full scope including infra recommendations (user decision)

### Deferred Issues
| Issue | Origin | Effort | Revisit |
|-------|--------|--------|---------|
| Tenant SMTP credential encryption at rest | Audit 01-01 | S | When DB-level encryption reviewed |
| Email rate limiting on internal endpoint | Audit 01-01 | S | Phase 2 or when abuse patterns emerge |
| Email content sanitization (XSS) | Audit 01-01 | S | When user-supplied HTML templates added |
| Scheduler health check alert | 01-02 | S | When monitoring enhancements planned |
| SCRIPT/REPORT_EXPORT job types | 01-02 | M | When those features are built |
| IP restriction enforcement in gateway | 01-03 | M | Phase 2 (Enterprise Security) |
| Scope enforcement in RouteAuthorizationFilter | 01-03 | M | Phase 2 (Enterprise Security) |
| Dynamic OAuth2 client registration on app create | 01-03 | M | Next connected apps enhancement |
| Database TLS (sslmode=require) | Security Audit | S | Phase 1B-02 infra doc |
| Redis TLS | Security Audit | S | Phase 1B-02 infra doc |
| Service-to-service mTLS | Security Audit | M | Phase 1B-02 infra doc |
| K8s network policies | Security Audit | S | Phase 1B-02 infra doc |

### Blockers/Concerns
None.

## Session Continuity

Last session: 2026-03-22
Stopped at: Phase 1A complete, Phase 1B plan ready
Next action: /paul:apply .paul/phases/01b-security-hardening/01b-01-PLAN.md
Resume file: .paul/phases/01b-security-hardening/01b-01-PLAN.md

---
*STATE.md — Updated after every significant action*
