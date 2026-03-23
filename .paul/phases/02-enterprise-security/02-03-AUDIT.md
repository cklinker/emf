# Enterprise Plan Audit Report

**Plan:** .paul/phases/02-enterprise-security/02-03-PLAN.md
**Audited:** 2026-03-22
**Verdict:** Conditionally acceptable (now acceptable after upgrades applied)

---

## 1. Executive Verdict

The plan correctly adds authenticated file streaming to replace unauthenticated presigned URLs. The architecture (S3Client streaming, tenant scoping, 404-not-403) is sound. However, the original plan had critical security gaps: no path traversal prevention (OWASP #1 file handling vulnerability), no resource cleanup for S3 streams, and no filename sanitization. After applying 2 must-have and 3 strongly-recommended upgrades, the plan is enterprise-ready.

## 2. What Is Solid

- **Tenant scoping with 404** — Returning 404 instead of 403 for cross-tenant access prevents tenant enumeration. Correct security decision.
- **Separate S3Client for internal endpoint** — Presigner uses public endpoint for browser URLs; S3Client uses internal for server-side streaming. Clean separation.
- **ConditionalOnBean activation** — FileController only activates when S3StorageService is available. Doesn't break non-S3 deployments.
- **Range request support** — Proper for large file serving. Using S3's built-in range parameter is efficient.
- **Presigned URLs preserved** — AttachmentUrlEnricher unchanged. Backward compatible.

## 3. Enterprise Gaps Identified

1. **Path traversal vulnerability** — `GET /api/files/**` captures arbitrary path. `../` sequences in the storageKey could escape tenant directory boundaries. This is OWASP A01:2021 (Broken Access Control). The tenant prefix check is insufficient: `tenant-A/../tenant-B/secret.pdf` would have `tenant-A` as the first segment but access `tenant-B` files.

2. **S3 InputStream leak** — `StorageObject` wraps `ResponseInputStream<GetObjectResponse>` which holds an HTTP connection. If not closed in a finally block, connection pool exhaustion will occur under load or on errors.

3. **Content-Disposition header injection** — Filenames from storageKey could contain `\r\n` (response splitting), quotes, or semicolons that break Content-Disposition parsing or enable XSS.

4. **text/html inline serving = XSS** — The plan lists text/html as an inline type. Serving user-uploaded HTML inline allows stored XSS attacks. HTML must be served as attachment.

5. **No audit trail for file access** — File downloads are security-relevant operations. No logging means incident investigation for data exfiltration is impossible.

## 4. Upgrades Applied to Plan

### Must-Have (Release-Blocking)

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 1 | Path traversal prevention | AC (added AC-7), Task 2 step 1 | Reject storageKeys containing ".." or "%2e", log security event |
| 2 | InputStream resource cleanup | AC (added AC-8), Task 2 streaming step | Must use try-with-resources or finally block |

### Strongly Recommended

| # | Finding | Plan Section Modified | Change Applied |
|---|---------|----------------------|----------------|
| 3 | Filename sanitization | AC (added AC-9), Task 2 step 2 | Strip special chars, allow only [a-zA-Z0-9._-] |
| 4 | text/html not inline | Task 2 step 2, avoidance rules | Serve HTML as attachment to prevent XSS |
| 5 | File access audit logging | AC (added AC-10), Task 2 streaming step | Log user, storageKey, result, size via security.audit |

### Deferred (Can Safely Defer)

| # | Finding | Rationale for Deferral |
|---|---------|----------------------|
| 1 | maxFileSize enforcement on download | File size is already constrained at upload time. Download-side enforcement is defense-in-depth but not critical for v1. |

## 5. Audit & Compliance Readiness

With AC-7 (path traversal) and AC-10 (audit logging), the plan meets OWASP API Security requirements for file access control. Path traversal prevention addresses OWASP A01:2021. Audit logging enables post-incident reconstruction of file access patterns.

## 6. Final Release Bar

**Must be true:** Path traversal blocked, InputStreams closed, filenames sanitized, HTML served as attachment, file access logged.

**Sign-off:** With applied upgrades, I would approve this for production.

---

**Summary:** Applied 2 must-have + 3 strongly-recommended upgrades. Deferred 1 item.
**Plan status:** Updated and ready for APPLY

---
*Audit performed by PAUL Enterprise Audit Workflow*
*Audit template version: 1.0*
