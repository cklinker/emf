# Slice 7 — Public-Traffic Hardening

> Child of `specs/consumer-alerting/README.md`. Depends on slice 5 (the endpoints it
> protects). **Security-typed → NO auto-merge. Gate for enabling portal self-signup in
> production.**

> **Partially pre-empted by slice 1A**, which had to fix the per-IP limiter to make the
> billing webhook path work at all. Already landed:
> - `IpRateLimitFilter` matches **longest path prefix** (it was exact `Set.contains`, so only
>   a literal `/actuator/health` was ever limited), takes budgets from
>   `kelta.gateway.rate-limit.ip-paths` as `<prefix>=<per-minute>`, and gives each prefix its
>   own per-IP bucket so one endpoint's burst cannot exhaust another's budget.
> - `Retry-After` reports the real remaining window rather than the full window length.
> - **IP/CIDR exemption** (`kelta.gateway.rate-limit.exempt-cidrs`, env
>   `RATE_LIMIT_EXEMPT_CIDRS`) honoured by **both** the per-IP and per-tenant limiters, via
>   `RateLimitExemptionService`. The CIDR matcher was extracted out of
>   `TenantIpAllowlistFilter` into shared `io.kelta.gateway.net.CidrBlock` — one
>   implementation, address-literals only so a hostname in a forwarded header cannot trigger
>   a DNS lookup.
>
> Still open for this slice: making the per-IP limiter **Redis-backed** (it remains in-memory
> per pod, so N replicas mean N× the budget), the **per-user** window, route-class
> multipliers, the ALTCHA bot challenge, edge controls, and the public-surface sweep.

## 1. Goal & scope

Delivers: Redis-backed per-IP rate limiting for public paths (consistent across gateway
replicas), per-user rate limiting for authenticated traffic with entitlement-driven budgets, a
pluggable bot challenge on signup/login-request, and edge controls at the ingress layer.

Does not deliver: proprietary CAPTCHA services (ALTCHA is the open-source choice per the
project's open-source rule), product-grade bot management / WAF, or a public bulk-data API
(deliberately out of scope — parent Key Decisions).

## 2. UI samples

N/A — gateway/backend. Frontend contract: the signup and login-request forms embed the
self-hosted ALTCHA widget (no third-party calls) and send the solved challenge in the request
body.

## 3. Data & API contracts

**Per-IP limiter (public paths)** — replaces the current in-memory path set with Redis fixed
windows shared across replicas. **Reuse the corrected Lua pattern from `RedisRateLimiter`:
atomic INCR with the TTL set only when the window is new — never refresh the TTL per request**
(the regression class behind the 2026-07-11 lockout fix). Config path→budget map:

```yaml
kelta:
  gateway:
    rate-limit:
      ip-paths:
        "/portal/api/signup": 5/min
        "/portal/api/login/request": 10/min
        "/api/billing/webhooks": 120/min
```

429 + an honest `Retry-After` (the real remaining window TTL). Keyed on client IP; reuse the
`trust-forwarded-for` posture from `TenantIpAllowlistFilter`, tightened for limiter keys —
take the last untrusted hop rather than honoring an arbitrary forwarded chain.

**Per-user limiter (authenticated)** — a window keyed `tenantId:userId` beside the existing
tenant-keyed window (which stays as the outer cap), so one member's traffic cannot consume the
whole tenant budget. Budget from the `apiCallsPerDay` entitlement via slice-1
`EntitlementService` (DEFAULT-plan budget for free members; higher-tier plans get more);
INTERNAL users fall back to the tenant governor derivation. PAT traffic hits the same
per-user key.

**Route classes**: per-path-class multipliers (auth strictest, reads moderate) as config,
rather than one global divisor.

**Bot challenge**: a `BotChallengeVerifier` seam (interface + ALTCHA implementation —
self-hosted challenge issue/verify) enforced in the signup and login-request handlers when
`kelta.auth.bot-challenge.enabled=true`; a honeypot form field rejected server-side as a
cheap extra.

**Edge (deployment repo, separate PR)**: ingress rate-limit annotations on the public
virtualhost; optionally CrowdSec for behavioral banning; `robots.txt` allowing content pages
and disallowing API paths (advisory only — enforcement is the budgets above).

**Public-surface check**: an unauthenticated sweep of collection/aggregate endpoints must
return 401/404; the only public JSON stays the existing UI-bootstrap GETs plus the
webhook/tracking paths.

## 4. DB migrations

None (Redis + config only).

## 5. File-by-file code changes

- kelta-gateway: `IpRateLimitFilter` → Redis-backed with the config map (or a new
  `PublicPathRateLimitFilter` replacing it; keep order −150); `RateLimitFilter` gains the
  per-user window (order unchanged, −50); the Lua script shared with `RedisRateLimiter`
  (extract to one place — do not fork it); `application.yml` config blocks.
- kelta-auth: `BotChallengeVerifier` (+ ALTCHA implementation) wired into signup and
  login-request; config + secret for the challenge key.
- kelta-worker: none expected. The gateway's entitlement lookup is decided in-slice — the
  cheapest option is an internal call with a Caffeine cache plus the slice-1 NATS
  invalidation subject (if the gateway consumes it, the payload's reflect-config entry
  already exists from slice 1).
- Deployment repo (separate PR): ingress annotations, optional CrowdSec, `robots.txt` in the
  consumer frontend.

## 6. Test plan

Unit: Lua window semantics (TTL set only on a new window — regression guard), path-map
matching, per-user vs per-tenant key independence, `Retry-After` honesty, ALTCHA verification
(valid / expired / replayed challenge), honeypot rejection. Gateway integration: a burst from
IP A gets 429 while IP B proceeds; member A hammering `/api/watches` gets a per-user 429
without affecting member B or the tenant window. Live: cross-replica consistency (two gateway
pods sharing the Redis window) and the unauthenticated public-surface sweep.

## 7. Docs to update

`architecture.md` (filter chain — new/changed orders, per-user limiter semantics) ·
`concerns.md` (public-traffic controls row; the forwarded-header limiter-key posture) ·
`README.md` env vars · `status.md` rate-limiting row.

## 8. Risks & open questions

Redis availability — the limiter fails open (matching the existing posture) but must log
loudly. The gateway's entitlement lookup adds a cross-service dependency — cache hard, fail
open to the tenant budget. ALTCHA difficulty needs tuning (too low is useless, too high hurts
mobile) — config-driven, start modest. The webhook budget must comfortably exceed a payment
processor's realistic retry bursts.
