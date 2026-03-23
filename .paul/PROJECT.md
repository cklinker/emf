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

---
*Created: 2026-03-22*
*Updated: 2026-03-22*
