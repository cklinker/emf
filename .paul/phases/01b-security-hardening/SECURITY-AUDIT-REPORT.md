# Kelta Platform — Security Audit Report

**Date:** 2026-03-22
**Scope:** Full platform audit (code + infrastructure)
**Auditor:** Automated security analysis

---

## Executive Summary

12 findings identified across code, CI, and infrastructure layers. 5 high-priority code-level findings have been fixed (plan 01b-01). This report documents all findings, their resolution status, and infrastructure recommendations.

## Findings — Code Level (FIXED)

All code-level findings were addressed in PR #585.

| # | Finding | Severity | Status | Fix |
|---|---------|----------|--------|-----|
| 1 | Session cookies missing httpOnly/secure/sameSite | High | FIXED | Added cookie properties to application.yml |
| 2 | Content-Security-Policy header missing | High | FIXED | Added CSP to SecurityHeadersFilter |
| 3 | CORS wildcard headers in auth service | High | FIXED | Explicit header list in CorsConfig |
| 4 | JSON error responses use String.format | High | FIXED | ObjectMapper in JwtAuthenticationFilter |
| 5 | Federation falls back to no-op without encryption | High | FIXED | Fail-fast IllegalStateException |

## Findings — CI/Code (IMPLEMENTED)

| # | Finding | Severity | Status | Fix |
|---|---------|----------|--------|-----|
| 6 | No dependency vulnerability scanning | Medium | IMPLEMENTED | OWASP Dependency-Check added to Maven + CI |
| 7 | No security event audit logging | Medium | IMPLEMENTED | SecurityAuditLogger + SecurityAuditFilter |

## Findings — Public Endpoint Audit

| Endpoint | Method | Auth Required | Data Exposed | Risk |
|----------|--------|---------------|--------------|------|
| `/api/ui-pages` | GET | No | Page layouts, menu structure | Low — UI configuration only, no secrets |
| `/api/ui-menus` | GET | No | Menu items, navigation labels | Low — UI configuration only |
| `/api/oidc-providers` | GET | No | Provider names, login URLs | Low — public by design (login page) |
| `/api/tenants` | GET | No | Tenant name, slug | Low — public by design (tenant selection) |

**Assessment:** All public endpoints return only UI configuration or public-facing data. No secrets, internal IDs, or configuration details are exposed. The current public path set is appropriate.

## Infrastructure Recommendations

### 1. Database TLS (Priority: High, Effort: Small)

**Current state:** PostgreSQL connection strings use default protocol without explicit SSL mode.

**Recommendation:**
- Add `?sslmode=require` to all JDBC connection strings in Kubernetes secrets
- For production: use `sslmode=verify-full` with CA certificate
- Example: `jdbc:postgresql://db-host:5432/kelta?sslmode=require`

**Where to change:**
- Kubernetes secret: `kelta-db-credentials` in namespace `kelta`
- Environment variable: `DB_URL` in gateway, worker, and auth deployments

### 2. Redis TLS (Priority: High, Effort: Small)

**Current state:** Redis connections configured via `spring.data.redis.host/port` without TLS.

**Recommendation:**
- Enable TLS on Redis deployment (or use a managed Redis with TLS)
- Configure Spring Redis SSL:
  ```yaml
  spring:
    data:
      redis:
        ssl:
          enabled: true
  ```
- If using self-signed certs, configure trust store

**Where to change:**
- Redis deployment configuration
- `application.yml` in gateway, worker, auth services

### 3. Service-to-Service mTLS (Priority: Medium, Effort: Medium)

**Current state:** Internal service communication uses plaintext HTTP.
- Gateway → Worker: `http://kelta-worker:80`
- Gateway → Cerbos: `cerbos.emf.svc.cluster.local:3593` (gRPC, no TLS)

**Recommendation:**
- **Option A (Preferred):** Deploy Istio or Linkerd service mesh for automatic mTLS
  - Zero application changes required
  - Covers all service-to-service communication
- **Option B:** Configure explicit TLS on each service
  - Requires certificate management (cert-manager)
  - Update connection URLs to use `https://` / TLS-enabled gRPC

**Where to change:**
- ArgoCD manifests in `homelab-argo` repo
- Namespace: `kelta`

### 4. Kubernetes Network Policies (Priority: Medium, Effort: Small)

**Current state:** No NetworkPolicy resources restricting traffic to `kelta` namespace.

**Recommendation:**
Create NetworkPolicy to restrict ingress:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: kelta-ingress-policy
  namespace: kelta
spec:
  podSelector: {}
  policyTypes:
    - Ingress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: kelta
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: ingress-nginx
```

**Where to change:**
- ArgoCD manifests in `homelab-argo` repo

### 5. Secret Rotation Procedures (Priority: Medium, Effort: Medium)

**Current state:** No documented procedures for rotating:
- `KELTA_ENCRYPTION_KEY` (AES-256 master key)
- `JWK_SET` (JWT signing RSA keys)
- `DB_PASSWORD` (PostgreSQL credentials)
- OAuth2 client secrets

**Recommendation:**
Document rotation procedures for each secret:
1. **KELTA_ENCRYPTION_KEY:** EncryptionService supports version prefix (`enc:v1:...`). Implement key version rotation by adding `enc:v2:` support.
2. **JWK_SET:** Publish new key to JWKS endpoint with overlap period (both old and new keys valid during transition).
3. **DB_PASSWORD:** Standard PostgreSQL `ALTER USER ... PASSWORD ...` with rolling restart.
4. **OAuth2 secrets:** Re-register clients with new secrets, revoke old tokens.

### 6. GDPR Data Retention (Priority: Low, Effort: Large)

**Current state:** No data retention policies or PII deletion endpoints implemented.

**Recommendation:**
- Implement `DELETE /api/users/{id}` with cascading data removal
- Add configurable retention periods per collection
- Create scheduled job for expired data cleanup
- Document data processing activities for compliance

---

## Compliance Summary

| Standard | Status | Notes |
|----------|--------|-------|
| OWASP API Security Top 10 | Partial | Rate limiting, auth, input validation in place. Missing: excessive data exposure review |
| NIST SP 800-63B | Compliant | Password policies follow NIST guidelines |
| OAuth 2.0 / OIDC | Compliant | Spring Authorization Server with standard flows |
| HTTPS/HSTS | Implemented | HSTS header with 1-year max-age |

## Next Steps

1. Address infrastructure recommendations (items 1-4) in ops/ArgoCD repo
2. Document secret rotation procedures (item 5)
3. Plan GDPR data retention (item 6) for future milestone
4. Schedule periodic re-audits (quarterly recommended)

---
*Security Audit Report — 2026-03-22*
*Phase 1B: Security Hardening*
