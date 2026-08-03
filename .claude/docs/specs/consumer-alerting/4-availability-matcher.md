# Slice 4 — Availability Matcher + Alert Fanout

> Child of `specs/consumer-alerting/README.md`. Depends on slice 3. This is the alert hot
> path — the latency budget is seconds from poller publish to push/email send.

## 1. Goal & scope

Delivers: the `KELTA_AVAILABILITY` stream + `AvailabilityEventListener` (normalize →
transition detection with an episode model → indexed watch query → alert dedupe →
`AlertDispatchService` push/email fanout with delivery tracking) + a post-fanout republish to
`kelta.trigger.<tenantId>.availability` for tenant flows.

Does not deliver: the SMS channel (later — the dispatch service leaves a seam), quiet-hours
hold-and-release (v1 is per-watch channel prefs), the poller itself (outside this repo;
contract in the parent), reporting aggregates.

Conforms to parent: dedicated subject (the hot path is not on `kelta.trigger.>`), the
AvailabilityEvent shared contract, and the flows-off-the-hot-path rule.

## 2. UI samples

N/A — backend. Sample push payload: title `Now available: <target name>`, body
`<window> just opened.`, data `{watchId, targetId, slotKey, deepLink}`.

## 3. Data & API contracts

Consumes `kelta.availability.event.<tenantId>.<source>` (AvailabilityEvent, parent §Shared
contracts) on queue group `worker-availability` (exactly one pod per event; the
`NatsTriggerFlowListener` registration idiom in `NatsSubscriptionConfig`).

Pipeline per event:
1. **Resolve target** by `(tenant, source, targetExternalId)`; unknown target → debug-log +
   drop (a poller may cover targets no tenant has registered).
2. **Upsert `availability_state`**; compute the transition. CLOSED→OPEN mints a new
   `episode_id`. No transition → stop (the volume killer: most polls report no change).
3. **Match**: `SELECT … FROM watch WHERE tenant_id=? AND target_id=? AND status='ACTIVE'`
   plus date-window overlap pushed into SQL (`window_start <= criteria.dateEnd AND window_end
   >= criteria.dateStart` when the event carries a window); residual criteria (quantity,
   minDuration) evaluated in Java.
4. **Dedupe**: `INSERT INTO alert … ON CONFLICT (tenant_id, watch_id, slot_key, episode_id)
   DO NOTHING`; plus a suppression-window guard (`NOT EXISTS` same watch+slot within
   `kelta.availability.suppression-minutes`, default 30) against episode flapping.
5. **Fanout** (`AlertDispatchService`): for each surviving alert × watch channel ∩ the
   member's entitled channels (`EntitlementService.listLimit(…, "channels")`): create an
   `alert_delivery` PENDING row → send (push via `DefaultPushService.sendToUser`, email via
   `DefaultEmailService` with the tenant template `availability.alert`) → mark SENT or
   FAILED+error. Sends happen outside the DB transaction; a send failure never rolls back the
   alert row (no duplicate alert on retry).
6. **Bridge**: fire-and-forget republish of a compact summary `{targetId, targetName, slotKey,
   window, matchedWatches}` to `kelta.trigger.<tenantId>.availability` — tenant flows (digest,
   ticker, custom automations) hang off this, never off the hot path.

## 4. DB migrations

None beyond slice 3 (tables exist). If EXPLAIN shows the match query wants a composite partial
index (`watch (tenant_id, target_id) WHERE status='ACTIVE'`), add head+1.

## 5. File-by-file code changes

- `JetStreamInitializer.java` — `ensureStream("KELTA_AVAILABILITY", ["kelta.availability.>"],
  ~1h maxAge)` (its own stream — an existing stream's subjects cannot be extended).
- Worker `listener/AvailabilityEventListener.java` — NEW; registered in
  `NatsSubscriptionConfig` (queue group `worker-availability`).
- Worker `service/availability/{AvailabilityMatchService, AlertDispatchService}` — NEW.
- Payloads: the inbound body is poller-authored JSON parsed as `JsonNode`/record — if a
  `PlatformEvent` wrapper is used anywhere, its payload record needs reflect-config entries in
  worker AND gateway (native rule). The outbound trigger bridge publishes a plain Map (the
  presence precedent — no new reflect entries).
- Config: `kelta.availability.suppression-minutes`, `kelta.availability.enabled`.
- Email template `availability.alert` (tenant-overridable) — seed in code if templates are
  code-seeded, else document as tenant setup.

## 6. Test plan

Unit: transition/episode logic (CLOSED→OPEN mints, OPEN→OPEN no-op, flap inside the
suppression window suppressed); the criteria-overlap SQL builder; dispatch channel
intersection; send failure → delivery FAILED without alert rollback. Harness:
`AvailabilityMatchScenarioTest` — real DB + NATS: publish the same event twice → one alert
row; a second OPEN episode after CLOSED → a second alert; two members watching the same target
both alerted; RLS isolation. Live check (post-deploy): synthetic event → push/email received +
trigger republish observed; poison-message `maxDeliver` behavior sane.

## 7. Docs to update

Root `CLAUDE.md` messaging table (`kelta.availability.event.*`, the trigger bridge row) +
stream list · `integrations.md` availability/poller contract section · `status.md` alerting
row → working · `concerns.md` for any accepted trade-off (e.g. the suppression default).

## 8. Risks & open questions

Event storms (a source flapping across many targets) — the queue group + transition
short-circuit bound the work; stream maxAge bounds replay. Duplicate pollers double-publishing
— the dedupe tuple makes it harmless. Tenant email templates must exist before go-live
(tenant config task). Per-target watch fanout is unbounded — fine at launch scale; revisit
batching past ~10k watches on one target.
