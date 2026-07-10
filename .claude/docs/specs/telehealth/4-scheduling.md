# [Slice 4] — Scheduling & Visit Links

> Child spec of [Telehealth parent](./README.md), to be refined with the implementation.
> Conforms to parent §Shared contracts, §Security. Depends on slice 1 (portal users book /
> get invited). Backend + FE.

## 1. Goal & scope

Delivers scheduled visits between a provider (internal) and a portal user:

- System collections: `telehealth-availability` (weekly rules + date exceptions per
  provider) and `telehealth-appointments` (portalUserId, providerId, scheduledStart/End,
  status `REQUESTED|CONFIRMED|CANCELLED|COMPLETED|NO_SHOW`, visitType, reason,
  conversationId?, videoSessionId? — video fields wired in slice 5).
- **Slot engine**: `GET /api/telehealth/slots?providerId&from&to&duration` computes
  availability minus booked appointments; `POST /api/telehealth/appointments` books with a
  conflict check that survives concurrent requests (constraint/locking, not read-then-write).
- **Booking surfaces**: page-builder widget `appointment-scheduler` (portal picks provider →
  slot → confirms; auto-creates the participant share via slice 1); staff side books from
  the appointment collection UI + `CalendarMonthView` on `scheduledStart` (existing saved
  calendar views).
- **Reminders & confirmations**: seeded, tenant-editable flow templates — confirmation email
  on CONFIRMED (with RFC 5545 `.ics` attachment), reminder at T-minus offset via
  `DelayActionHandler` (`delayUntilField: scheduledStart` minus configured minutes),
  cancellation notice. Seeded email templates `appointment_confirmed`,
  `appointment_reminder`, `appointment_cancelled` with a signed **visit link**.
- **Visit links**: `GET /api/telehealth/visits/{signedToken}` (public path) — HMAC token
  bound to (tenant, appointment, portal user), valid only in the appointment window ±
  grace, redirects into the portal app's appointment page (magic-link auth if no session).

Does NOT deliver: recurrence, group visits, external calendar sync (Google/CalDAV — the
`.ics` attachment is the v1 interop), SMS reminders, video session creation (slice 5).

## 2. UI samples

Widget: 3-step wizard (provider/visit type → date grid with free slots in the portal user's
timezone → confirm + reason). Staff: appointments list + calendar view; appointment record
detail shows status timeline, linked conversation, reschedule/cancel actions.

## 3. Data & API contracts

```
GET  /api/telehealth/slots?providerId&from&to&duration=30 → {slots:[{start,end}]}   (tenant TZ math server-side)
POST /api/telehealth/appointments {providerId, start, visitType, reason?}           → 201 (portal: self; staff: any portalUserId)
POST /api/telehealth/appointments/{id}/cancel|reschedule|complete
GET  /api/telehealth/visits/{token}                                                 → 302 into portal appointment page
```

Availability shape: weekday, startTime, endTime, timezone, effectiveFrom/To, plus exception
rows (date, closed|window-override). Slot computation is pure worker logic (unit-testable);
booking enforces `status IN (REQUESTED, CONFIRMED)` non-overlap per provider. Portal authz:
own appointments only (participant share on create + in-controller checks on the scoped
endpoints, same idiom as chat).

## 4. DB migrations

One migration (verify head): two tables + RLS + indexes
(`(tenant_id, provider_id, scheduled_start)` and an overlap-guard constraint — exclusion
constraint on tstzrange per provider where status active, or equivalent locking strategy
decided at implementation).

## 5. File-by-file code changes (sketch)

| Area | Files |
|------|-------|
| runtime-core | `SystemCollectionDefinitions` +2 collections |
| kelta-worker | `TelehealthSchedulingController` + `SlotService` + `AppointmentService` (JdbcTemplate repos, conflict-safe booking), `AppointmentHook` (participant share, NATS via record.changed only — no custom subject needed), `.ics` generator util in the email path, seeded flow + email templates, gateway static route registration `/api/telehealth/**`, visit-token service (HMAC, campaign precedent), audit events |
| kelta-ui | `appointment-scheduler` widget descriptor; appointments admin/list config; calendar saved-view seed |
| i18n | `telehealth.scheduling.*` |

## 6. Test plan

- Worker unit: slot math (rules + exceptions + TZ/DST edges), double-book race (constraint
  path), visit-token window binding + single-use, `.ics` output validity (RFC 5545 lint).
- Harness: RLS on appointments; portal reads own only; booking conflict under two concurrent
  transactions (real Postgres).
- Vitest: widget wizard flow with mocked slots.
- Playwright (post-deploy, skip-gated): portal books via widget → confirmation email in
  mailpit with `.ics` + visit link → link lands authenticated on the appointment page;
  staff sees it on the calendar; reminder flow fires (delay shortened in test config).

## 7. Docs to update (same PR)

`status.md`; `integrations.md` (`.ics` + visit links); `architecture.md`
(`/api/telehealth/**` authz row + static route); `concerns.md` if the exclusion-constraint
approach adds operational notes.

## 8. Risks & open questions

- Timezone/DST correctness is the bug farm — all persistence UTC, slot expansion in the
  provider's zone, rendering in the viewer's; DST-transition tests are mandatory.
- Overlap enforcement choice (Postgres exclusion constraint vs advisory lock) decided at
  implementation; the harness race test pins the behavior either way.
- Reminder flows are tenant-editable seeds — a tenant deleting the flow silently loses
  reminders; note in the admin UI (badge "reminders disabled") as a stretch.
- Provider identity = platform user in v1; a separate "provider directory" collection
  (specialties, display bios for the widget) is deferred unless the widget needs it.
