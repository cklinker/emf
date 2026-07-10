# [Slice 5] — Video Backend (LiveKit)

> Child spec of [Telehealth parent](./README.md), to be refined with the implementation.
> Conforms to parent §Key architecture decisions, §Security, §Deployment. Depends on
> slice 4 (appointments). **Security-typed PR — never auto-merged.** Backend + infra.

## 1. Goal & scope

Delivers video-session lifecycle on self-hosted LiveKit:

- **Infra**: LiveKit ArgoCD app in `~/GitHub/homelab-argo` (Deployment, Service, Ingress
  `livekit.rzware.com`/`livekit.kelta.io` for wss signaling, embedded TURN with TLS
  fallback, node UDP media range; image via Harbor; keys in a Secret). Local dev:
  `docker-compose.yml` `telehealth` profile + `make up-telehealth`.
- `video-sessions` system collection: appointmentId?, conversationId? (ad-hoc escalation
  from chat), roomName (opaque UUID), status `CREATED|ACTIVE|ENDED`, startedAt, endedAt,
  durationSeconds, recordingConsent, recordingKey?.
- **Token mint**: `POST /api/telehealth/sessions/{id}/token` — authorizes the caller
  (appointment provider/portal participant or conversation participant), checks the
  `telehealth` feature flag + `videoMinutesPerMonth` governor, then mints a short-TTL HS256
  LiveKit access token (identity = userId, display name, room-scoped join/publish/subscribe
  grants). Hand-minted JWT with existing libs — no vendor SDK dependency.
- **Webhooks**: `POST /api/telehealth/webhooks/livekit` (public path, signature-verified,
  idempotent) — `room_started`/`participant_joined`/`room_finished`/`egress_ended` drive
  session status, compute minutes into the governor usage counter, publish
  `kelta.video.session.<tenantId>.<sessionId>` (NATS_TRIGGERED flows: post-visit follow-up,
  no-show detection), and emit audit events.
- **Recording (optional)**: per-tenant flag + per-visit consent (captured in slice 6); room
  composite egress → tenant-prefixed S3 → `egress_ended` webhook attaches the file as an
  attachment record on the appointment; retention via the attachment lifecycle.
- Sessions auto-created for CONFIRMED appointments at booking or first-join (implementation
  decides; spec default: lazily at first token request).

Does NOT deliver: any UI (slice 6), SIP/phone dial-in, live transcription (LiveKit agents —
possible v2 with kelta-ai), multi-region SFU.

## 2. Sample payloads

```
POST /api/telehealth/sessions/{id}/token → {url:"wss://livekit.<domain>", token:"<jwt>", expiresAt}
webhook room_finished → session ENDED, durationSeconds set, usage counter += ceil(minutes), NATS event published
```

LiveKit grant claims (video): `{roomJoin:true, room:"<roomName>", canPublish:true,
canSubscribe:true}`; TTL ≈ appointment window + grace.

## 3. Data & API contracts

Parent §Shared contracts lists the endpoints/subject. Governor: `videoMinutesPerMonth` in
`TenantTierQuotas` + Redis `video-minutes-monthly:<tenantId>:<yyyy-MM>` (the ai-tokens
pattern); token mint rejects at cap with the 429 governor envelope. Credentials: LiveKit API
key/secret in the platform credential store (encrypted, `kelta.config.credential.changed`
invalidation), configured per environment — not per tenant (single shared SFU; rooms are
tenant-namespaced `t_<tenantId>_<uuid>` and tokens are room-scoped).

## 4. DB migrations

One migration (verify head): `video-sessions` table + RLS + indexes
(`(tenant_id, appointment_id)`, `(tenant_id, status)`); webhook idempotency table (event id,
processed_at) or Redis SETNX — decided at implementation.

## 5. File-by-file code changes (sketch)

| Area | Files |
|------|-------|
| runtime-core | `SystemCollectionDefinitions` +`video-sessions` |
| kelta-worker | `VideoSessionController` (token mint), `VideoSessionService`, `LiveKitTokenService` (HS256 mint), `LiveKitWebhookController` + signature verifier + idempotency, governor key + usage counter, egress→attachment wiring, audit events (`VIDEO_TOKEN_ISSUED`, `VIDEO_SESSION_STARTED/ENDED`) |
| kelta-gateway | public path for the webhook; `/api/telehealth/**` static route already registered (slice 4) |
| runtime-events | `VideoSessionPayload` |
| homelab-argo (separate repo PR) | livekit app manifests |
| repo root | docker-compose `telehealth` profile, Makefile target, README env keys |

## 6. Test plan

- Worker unit: token grants/TTL/room scoping, authz matrix (provider ok, portal participant
  ok, stranger 403, feature-off 403, governor-cap 429), webhook signature accept/reject +
  idempotent replay, minutes computation.
- Harness: session RLS; webhook → status transition → NATS publish (Testcontainers NATS);
  egress → attachment row.
- Manual/deploy smoke: two browsers join a dev-stack room (documented in README); Playwright
  automation lands in slice 6 with fake-media flags.
- Infra: ArgoCD sync + `livekit-cli` connection test documented in the runbook.

## 7. Docs to update (same PR)

CLAUDE.md Messaging table (+`kelta.video.session.*`); `integrations.md` LiveKit section
(deploy, token shape, webhooks, recording); `architecture.md` trust boundary; `ci-cd.md` if
the compose profile touches CI; `README.md` (profile + env); `concerns.md` (SFU ops:
UDP/TURN reachability, single-node capacity); `status.md`.

## 8. Risks & open questions

- **Patient-network reachability** is the classic failure: corporate/hospital NATs block
  UDP — TURN-TLS on 443 must be enabled and smoke-tested from a restricted network before
  calling this shipped.
- Single shared SFU across tenants is an accepted v1 trade (room-scoped tokens isolate
  access; media isn't tenant-RLS'd by nature) — document in `concerns.md`; per-tenant
  keys/instances only if a compliance requirement demands it.
- Recording consent semantics (both parties? per-tenant policy?) — v1: recording only
  starts after all participants consent (slice 6 captures it); operators needing
  one-party-consent rules configure per tenant. Legal posture is the operator's.
- LiveKit version pinning + upgrade cadence goes in the homelab-argo runbook.
