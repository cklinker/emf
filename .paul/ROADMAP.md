# Roadmap: Kelta

## Overview
Close competitive gaps identified from Appwrite and Strapi analysis while preserving Kelta's unique strengths (multi-tenancy, Cerbos auth, flow engine, observability). Focus on features that unblock core workflows, then enterprise security, developer experience, and realtime capabilities.

## Current Milestone
**v1.0 Competitive Parity** (v1.0.0)
Status: In progress
Phases: 2 of 7 complete

## Phases

| Phase | Name | Plans | Status | Completed |
|-------|------|-------|--------|-----------|
| 1 | Foundation Gaps | 4 | Complete | 2026-03-22 |
| 1A | Namespace Alignment | 1 | Complete | 2026-03-22 |
| 1B | Security Hardening | 2 | Planning | - |
| 2 | Enterprise Security | 3 | Not started | - |
| 3 | Developer Experience | 3 | Not started | - |
| 4 | Realtime & Messaging | 3 | Not started | - |
| 5 | Advanced Platform | 2 | Not started | - |

## Phase Details

### Phase 1: Foundation Gaps
**Goal:** Unblock core workflows that are currently broken or missing
**Depends on:** Nothing (first phase)
**Research:** Likely (JSON:API bulk operations standard)

**Scope:**
- Email delivery integration (SMTP/SendGrid) — unblocks password resets, flow alerts, notifications
- Scheduled flow triggers — complete the existing stub (cron parsing exists)
- API keys / personal access tokens — programmatic access for CI/CD and integrations
- Enhanced password policies — history, dictionary, complexity rules

**Plans:**
- [x] 01-01: Email delivery integration (SMTP)
- [x] 01-02: Scheduled flow triggers
- [x] 01-03: Connected apps (OAuth2 Client Credentials)
- [x] 01-04: Enhanced password policies

### Phase 1A: Namespace Alignment
**Goal:** Align all Java filesystem directories with package declarations
**Depends on:** Phase 1 (complete Foundation Gaps first)
**Research:** None

**Scope:**
- Move 509 Java files to directories matching their package declarations
- Zero code changes — filesystem reorganization only
- Single PR covering all projects

**Plans:**
- [x] 01a-01: Move all Java files to match package declarations

### Phase 1B: Security Hardening
**Goal:** Address security audit findings at code and CI level, document infrastructure recommendations
**Depends on:** Phase 1A (namespace alignment — file paths change)
**Research:** None

**Scope:**
- Session cookie hardening, CSP header, CORS fix, JSON error safety, encryption enforcement
- OWASP Dependency-Check integration in CI
- Security event audit logging
- Infrastructure security recommendations document

**Plans:**
- [ ] 01b-01: Code-level security fixes (5 high-priority findings)
- [ ] 01b-02: CI security integration + infra recommendations

### Phase 2: Enterprise Security
**Goal:** Harden authentication and authorization for enterprise compliance
**Depends on:** Phase 1 (email delivery needed for MFA recovery)
**Research:** Unlikely (established patterns)

**Scope:**
- MFA / TOTP authentication with recovery codes
- Field-level security enforcement (strip HIDDEN fields from API responses)
- Direct file serving endpoint (stream files through API)

**Plans:**
- [ ] 02-01: MFA / TOTP authentication
- [ ] 02-02: Field-level security enforcement
- [ ] 02-03: Direct file serving endpoint

### Phase 3: Developer Experience
**Goal:** Improve API capabilities and developer tooling
**Depends on:** Phase 1 (API keys needed for batch operations auth)
**Research:** Likely (JSON:API Atomic Operations extension for bulk)

**Scope:**
- Batch operations via JSON:API Atomic Operations extension
- Image transformations (resize, crop, format conversion)
- API documentation site (auto-generated from collection schemas)

**Plans:**
- [ ] 03-01: JSON:API Atomic Operations (bulk CRUD)
- [ ] 03-02: Image transformations
- [ ] 03-03: API documentation site

### Phase 4: Realtime & Messaging
**Goal:** Add live update capabilities and multi-channel messaging
**Depends on:** Phase 1 (email delivery foundation)
**Research:** Likely (WebSocket architecture with Kafka bridge)

**Scope:**
- WebSocket realtime subscriptions (permission-aware, Kafka-bridged)
- SMS authentication (Twilio integration for passwordless + MFA)
- Push notifications (APNs + FCM)

**Plans:**
- [ ] 04-01: WebSocket realtime subscriptions
- [ ] 04-02: SMS authentication
- [ ] 04-03: Push notifications

### Phase 5: Advanced Platform
**Goal:** Enterprise deployment flexibility and developer tooling
**Depends on:** Phase 2 (security foundation)
**Research:** Unlikely (established patterns)

**Scope:**
- Custom tenant domains (CNAME routing)
- CLI tool (full CRUD, deployment, migration)

**Plans:**
- [ ] 05-01: Custom tenant domains
- [ ] 05-02: CLI tool

---
*Roadmap created: 2026-03-22*
*Last updated: 2026-03-22 — Phase 1 + 1A complete*
