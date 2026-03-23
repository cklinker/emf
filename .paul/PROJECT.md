# Project: Kelta

## Description
A multi-tenant enterprise platform (Kelta) with a Spring Boot backend, React builder UI, and Spring Cloud Gateway — enabling teams to define custom objects, fields, permissions, and workflows through configuration rather than code.

## Core Value
Teams can build and manage business applications through a multi-tenant enterprise platform with configurable objects, fields, permissions, and workflows — without custom development.

## Design Principles

1. **Standards everywhere** — Use open standards as the foundation: JSON:API, OIDC, OAuth2, SMTP (RFC 5321), OpenTelemetry, Cerbos, etc. Standards-compliant by default, not as an afterthought.

2. **Highly opinionated, customizable** — Pick the best default and ship it. Don't offer 5 options — offer 1 good one with an extension point. The out-of-the-box experience should work without configuration. Power users can swap components via SPIs.

3. **Open source only** — The default solution stack uses only open source products. No proprietary SDKs (SendGrid, Twilio, etc.) in the core path. Users can add proprietary integrations via extension points, but the platform ships with open source defaults.

## Constraints
- Open source only for default integrations
- Standards-compliant APIs and protocols
- Extension points (SPIs) for all integration boundaries

## Success Criteria
- Teams can build and manage business applications through configuration
- Platform works out-of-the-box with zero proprietary dependencies
- Every integration has a standards-based default and an SPI for customization

## Validated Requirements (Shipped)

- Email delivery integration (SMTP, per-tenant config) — Phase 1, Plan 01
- Scheduled flow triggers (cron-based, leader election) — Phase 1, Plan 02
- OAuth2 client credentials for connected apps (RFC 6749 §4.4) — Phase 1, Plan 03
- Per-tenant password policies (NIST SP 800-63B, dictionary, lockout) — Phase 1, Plan 04
- Java namespace alignment (filesystem matches package declarations) — Phase 1A, Plan 01

## Key Decisions

| Decision | Rationale | Phase |
|----------|-----------|-------|
| SMTP as default email (no SendGrid) | Open source only principle | 1-01 |
| SELECT FOR UPDATE SKIP LOCKED for scheduler | Standard PostgreSQL, no external coordinator | 1-02 |
| OAuth2 Client Credentials (not custom API keys) | RFC 6749 §4.4 standard | 1-03 |
| Redis jti set for token revocation | Near-instant revocation, simple implementation | 1-03 |
| NIST SP 800-63B password defaults | Industry standard, length > complexity | 1-04 |
| Bundled 10k dictionary (no HIBP API) | Open source only, no network dependency | 1-04 |
| Single PR for namespace alignment | Mechanical change, easier to review as one unit | 1A-01 |

---
*Created: 2026-03-22*
*Updated: 2026-03-22 after Phase 1A (Namespace Alignment)*
