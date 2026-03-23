---
phase: 04-realtime-messaging
plan: 01
subsystem: gateway
tags: [websocket, kafka, realtime, subscriptions, tenant-isolation]

requires:
  - phase: 01-foundation-gaps
    provides: Kafka infrastructure, RecordEventPublisher

provides:
  - WebSocket realtime subscriptions (ws://gateway/ws/realtime)
  - Kafka-to-WebSocket bridge for record change events
  - SubscriptionManager with tenant isolation and limits
  - JWT-authenticated WebSocket connections

affects: []

tech-stack:
  added: []
  patterns: [Kafka consumer → WebSocket fan-out, ConcurrentHashMap subscription registry, Sinks.Many for outbound messages]

key-files:
  created:
    - kelta-gateway/src/main/java/io/kelta/gateway/websocket/SubscriptionManager.java
    - kelta-gateway/src/main/java/io/kelta/gateway/websocket/RealtimeWebSocketHandler.java
    - kelta-gateway/src/main/java/io/kelta/gateway/config/WebSocketConfig.java
    - kelta-gateway/src/main/java/io/kelta/gateway/listener/RealtimeKafkaBridge.java
    - kelta-gateway/src/test/java/io/kelta/gateway/websocket/SubscriptionManagerTest.java

key-decisions:
  - "Raw WebSocket (no STOMP) — simpler, more flexible"
  - "In-memory subscriptions (per gateway instance, not distributed)"
  - "Separate Kafka consumer group: kelta-gateway-realtime"
  - "JWT via query param (WebSocket handshake doesn't support Authorization header in all browsers)"

duration: 25min
started: 2026-03-22T22:10:00Z
completed: 2026-03-22T22:35:00Z
---

# Phase 4 Plan 1: WebSocket Realtime Subscriptions Summary

**Added WebSocket realtime subscriptions with JWT auth, Kafka bridge, tenant isolation, and subscription/connection limits.**

## Performance

| Metric | Value |
|--------|-------|
| Duration | ~25 min |
| Tasks | 2 auto + 1 checkpoint |
| Files created | 5 |
| Lines added | 612 |

## Acceptance Criteria Results

| Criterion | Status | Notes |
|-----------|--------|-------|
| AC-1: JWT Auth | Pass | Token extracted from query param, validated via DynamicReactiveJwtDecoder |
| AC-2: Subscribe | Pass | JSON message protocol, confirmation response |
| AC-3: Unsubscribe | Pass | Removes from both forward and reverse index |
| AC-4: Event Delivery | Pass | Kafka bridge fans out to matching sessions |
| AC-5: Tenant Isolation | Pass | Routing key: tenantId:collectionName |
| AC-6: Permission-Aware | Partial | Subscription accepted; Cerbos check deferred to runtime wiring |
| AC-7: Connection Cleanup | Pass | removeSession cleans all subscriptions + decrements tenant count |
| AC-8: Heartbeat | Partial | Token expiration scheduling in place; WebSocket ping/pong deferred to runtime config |
| AC-9: Subscription Limit | Pass | Max 50 per session |
| AC-10: Field Security on Events | Deferred | Requires CerbosAuthorizationService in gateway (currently worker-only) |
| AC-11: Connection Limit | Pass | Max 100 per tenant |
| AC-12: JWT Expiration | Pass | Connection closed with code 4001 when token expires |

## Task Commits

| Task | Commit | Type | Description |
|------|--------|------|-------------|
| Tasks 1-2 | `f4d31702` | feat | WebSocket infra + Kafka bridge |

PR: #593 (auto-merge enabled)

## Deviations

| Type | Count | Impact |
|------|-------|--------|
| Partial ACs | 3 | AC-6 (Cerbos), AC-8 (ping/pong), AC-10 (field security) deferred to runtime |

## Next Phase Readiness

**Ready:** Plan 04-02 (SMS authentication) is next.

---
*Phase: 04-realtime-messaging, Plan: 01*
*Completed: 2026-03-22*
