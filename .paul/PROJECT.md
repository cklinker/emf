# Project: Kelta

## Description
A multi-tenant enterprise platform (Kelta) with a Spring Boot backend, React builder UI, and Spring Cloud Gateway — enabling teams to define custom objects, fields, permissions, and workflows through configuration rather than code.

## Core Value
Teams can build and manage business applications through a multi-tenant enterprise platform with configurable objects, fields, permissions, and workflows — without custom development.

## Design Principles

1. **Standards everywhere** — Use open standards as the foundation: JSON:API, OIDC, OAuth2, SMTP (RFC 5321), TOTP (RFC 6238), OpenTelemetry, Cerbos, etc.
2. **Highly opinionated, customizable** — Pick the best default and ship it. Extension points (SPIs) for customization.
3. **Open source only** — No proprietary SDKs in the core path. Users can add proprietary integrations via extension points.

## Validated Requirements (Shipped)

**Phase 1 — Foundation Gaps:**
- Email delivery (SMTP, per-tenant config)
- Scheduled flow triggers (cron, leader election)
- OAuth2 client credentials for connected apps (RFC 6749 §4.4)
- Per-tenant password policies (NIST SP 800-63B, dictionary, lockout)

**Phase 1A — Namespace Alignment:**
- Java filesystem directories match package declarations (509 files)

**Phase 1B — Security Hardening:**
- CSP, session cookies, CORS, JSON error safety, encryption enforcement
- OWASP Dependency-Check in CI, security audit logging

**Phase 2 — Enterprise Security:**
- MFA/TOTP authentication (RFC 6238) with encrypted secrets + recovery codes
- Bidirectional field-level security (read stripping + write enforcement via Cerbos)
- Direct file serving endpoint with streaming, path traversal prevention, range support

---
*Created: 2026-03-22*
*Updated: 2026-03-22 after Phase 2 (Enterprise Security)*
