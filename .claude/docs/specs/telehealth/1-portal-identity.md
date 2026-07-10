# [Slice 1] — Portal Identity & Record Access

> Child spec of [Telehealth parent](./README.md), to be refined with the implementation.
> Conforms to parent §Key architecture decisions, §Security. **Security-typed PR — never
> auto-merged.** Backend + auth; small admin-UI surface.

## 1. Goal & scope

Delivers the **external user** foundation every other slice depends on:

- `user_type ∈ {INTERNAL, PORTAL}` on `platform_user`, surfaced as a JWT claim and forwarded
  identity attribute.
- A seeded **Portal User** profile (API_ACCESS only; no collection CRUD, no setup
  permissions) provisioned for new tenants and backfilled for existing ones.
- **Passwordless magic-link login** for portal users (email one-time link/OTP), issued by
  kelta-auth; portal invites through the existing invite pipeline.
- A reusable **participant-share hook utility**: system collections can declare "creating a
  participant row grants the named user a `record_share` on the parent record" — the
  mechanism later slices use to scope patients to their own conversations/appointments.
- `maxPortalUsers` governor key, enforced at portal-user create/invite (staff `gov_users`
  stays tracked-only; changing that is out of scope).

Does NOT deliver: any chat/scheduling/video surface; self-service open registration
(portal users exist by invite, booking flow, or tenant IdP federation); SMS OTP
(email only — `SmsProvider` SPI hookup is deferred per parent).

## 2. UI samples

Admin Users page: a *Type* column (Internal/Portal badge) + "Invite portal user" action
(email + optional name; profile forced to Portal User). Portal login page (served on the
tenant slug/custom domain): email field → "Check your email" confirmation → link opens an
authenticated session; uniform response whether or not the email exists.

## 3. Data & API contracts

```
POST /api/auth/portal/request-link   {email}                    → 202 {status:"sent"} (always, uniform)
POST /api/auth/portal/exchange       {token}                    → session cookie/JWT redirect (auth-code style)
POST /api/admin/users/portal-invite  {email, firstName?, lastName?} → 201 user (PENDING_ACTIVATION, PORTAL)
```

- JWT gains `user_type` claim; gateway forwards `X-User-Type` alongside existing identity
  headers (header-transform filter).
- Magic-link token: single-use, ≤15 min expiry, HMAC-signed, bound to
  `(tenantId, userId, purpose=portal-login)`; storage + verification mirror the existing
  invite/campaign token patterns; consumed atomically. Request endpoint rate-limited
  per-email and per-IP.
- Participant-share utility (worker): `ParticipantShareSupport.grant(collectionId, recordId,
  userId, accessLevel)` writing `record_share` rows (USER type) — wraps
  `RecordShareRepository`; idempotent on re-invite.
- `TenantTierQuotas` gains `maxPortalUsers` (FREE small, ENTERPRISE larger, UNLIMITED ∞);
  create/invite paths throw the governor-exceeded error (429 envelope) when at cap.

## 4. DB migrations

One migration (**verify directory head first**; V167 at time of writing):
`ALTER TABLE platform_user ADD COLUMN user_type` (text, NOT NULL default `INTERNAL`, check
constraint), backfill, index on `(tenant_id, user_type)`; seed Portal User profile per
existing tenant (mirror the V162 `VIEW_ANALYTICS` per-profile seeding pattern); magic-link
token table (hashed token, purpose, expires_at, consumed_at, RLS).

## 5. File-by-file code changes (sketch — finalize with implementation)

| Area | Files |
|------|-------|
| kelta-auth | `PortalLoginController` (request-link/exchange), token service, `KeltaUserDetails` + JWT customizer for `user_type`, login-page template |
| kelta-worker | `AdminInviteController` portal path, `UserInviteService` portal branch (Portal User profile, portal email template), `ParticipantShareSupport`, `TenantTierQuotas` + governor enforcement, `TenantProvisioningHook` profile seed |
| kelta-gateway | header-transform filter forwards `X-User-Type`; no new routes (auth paths already routed) |
| kelta-ui | Users page type column + portal invite dialog; portal login page (unauthenticated shell) |
| Cerbos | principal attr `userType` available to future policies |

Email templates: `portal_invite`, `portal_login_link` (system-seeded, tenant-overridable).
Audit: `PORTAL_LINK_REQUESTED`, `PORTAL_LOGIN`, `PORTAL_USER_INVITED`.

## 6. Test plan

- kelta-auth unit: token single-use/expiry/purpose-binding; uniform not-found response;
  rate limit.
- Worker unit: portal invite forces profile + type; governor cap rejects at limit;
  `ParticipantShareSupport` idempotency.
- Harness (real Postgres/RLS): portal user + share grant reads parent record, non-participant
  denied; cross-tenant isolation on the token table.
- Playwright: invite → mailpit link → portal session lands on the tenant app; portal JWT
  gets 403 on `/api/admin/**` and on an unshared collection read.

## 7. Docs to update (same PR)

`status.md` Telehealth row (⚪ → 🟡 in-flight); `architecture.md` (portal auth flow, new
identity header); `conventions.md` (portal-endpoint authz idiom); `integrations.md` (magic
links); `SECURITY.md` review note; CLAUDE.md Messaging table unchanged (no new subjects).

## 8. Risks & open questions

- Magic-link deliverability depends on tenant SMTP health — surface send failures in
  `email-logs`; consider a resend cooldown UX.
- `user_type` on the JWT means token re-issue on type change (acceptable: type is immutable
  after create in v1).
- Decision taken (opinionated): portal users are **invited, not self-registered** in v1 —
  self-serve signup would open an enumeration/abuse surface this slice doesn't budget for;
  the booking flow (slice 4) can auto-invite.
- Seat semantics: portal seats counted separately from staff (`maxPortalUsers` vs
  `gov_users`) — pricing implications are an operator decision, keys are independent.
