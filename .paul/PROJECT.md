# Project: Kelta

## Description
A multi-tenant enterprise platform (Kelta) with a Spring Boot backend, React builder UI, and Spring Cloud Gateway — enabling teams to define custom objects, fields, permissions, and workflows through configuration rather than code.

## Core Value
Teams can build and manage business applications through a multi-tenant enterprise platform with configurable objects, fields, permissions, and workflows — without custom development.

## Design Principles

1. **Standards everywhere** — JSON:API, OIDC, OAuth2, SMTP (RFC 5321), TOTP (RFC 6238), OpenAPI 3.0, OpenTelemetry, Cerbos
2. **Highly opinionated, customizable** — Best defaults with extension points (SPIs)
3. **Open source only** — No proprietary SDKs in the core path

## Validated Requirements (Shipped)

**Phase 1:** Email (SMTP), scheduled triggers, OAuth2 connected apps, password policies (NIST SP 800-63B)
**Phase 1A:** Java namespace alignment (509 files)
**Phase 1B:** Security hardening (CSP, cookies, CORS, dependency scanning, audit logging)
**Phase 2:** MFA/TOTP, bidirectional field-level security, direct file serving
**Phase 3:** JSON:API Atomic Operations (bulk CRUD), image transformations, auto-generated OpenAPI docs

---
*Updated: 2026-03-22 after Phase 3 (Developer Experience)*
