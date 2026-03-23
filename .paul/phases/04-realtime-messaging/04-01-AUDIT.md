# Enterprise Plan Audit Report

**Plan:** .paul/phases/04-realtime-messaging/04-01-PLAN.md
**Audited:** 2026-03-22
**Verdict:** Conditionally acceptable (now acceptable after upgrades applied)

---

## 1. Executive Verdict

Strong architectural plan with correct Kafka-to-WebSocket bridging pattern. Four gaps found: subscription flooding (DoS), field-level security bypass on event data, connection limits, and JWT expiration on long-lived connections. After applying 2 must-have and 2 strongly-recommended upgrades, the plan is enterprise-ready.

## 2. What Is Solid

- **Separate Kafka consumer group** — Realtime events don't interfere with config processing.
- **In-memory subscriptions** — Correct for per-gateway-instance state. No distributed state needed.
- **JWT auth on handshake** — Secure connection establishment.
- **ConcurrentHashMap + CopyOnWriteArraySet** — Correct concurrent data structures.
- **Raw WebSocket (no STOMP)** — Simpler, more flexible for this use case.
- **Tenant isolation via routing key** — Kafka partitioning by tenantId:collectionName is a natural fit.

## 3. Enterprise Gaps

1. **Subscription flooding** — No limit on subscriptions per session. A malicious client could subscribe to every collection, causing memory exhaustion and excessive event fan-out on every Kafka message.
2. **Field-level security bypass** — Kafka events contain the full record `data`. If a user has HIDDEN fields, those fields are delivered via WebSocket but stripped via HTTP (CerbosFieldSecurityAdvice). Inconsistent security posture.
3. **Connection exhaustion** — No per-tenant connection limit. One tenant could exhaust gateway WebSocket capacity.
4. **Stale JWT** — WebSocket connections persist beyond JWT expiration. A revoked user could continue receiving events.

## 4. Upgrades Applied

### Must-Have

| # | Finding | Change Applied |
|---|---------|----------------|
| 1 | Subscription flooding | Max 50 subscriptions per session (AC-9) |
| 2 | Field-level security on events | Strip HIDDEN fields before WebSocket delivery (AC-10) |

### Strongly Recommended

| # | Finding | Change Applied |
|---|---------|----------------|
| 3 | Connection limit | Max 100 connections per tenant (AC-11) |
| 4 | JWT expiration | Close connection when token expires (AC-12) |

### Deferred

None.

## 5. Final Release Bar

With applied upgrades, I would approve this for production.

---

**Summary:** Applied 2 must-have + 2 strongly-recommended. Plan ready for APPLY.

---
*Audit performed by PAUL Enterprise Audit Workflow*
