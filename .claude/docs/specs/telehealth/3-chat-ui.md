# [Slice 3] — Chat UI: Internal Console + Portal Widget

> Child spec of [Telehealth parent](./README.md), to be refined with the implementation.
> Conforms to parent §Reuse Map, §Security. Depends on slice 2. Frontend-only.

## 1. Goal & scope

Delivers both chat surfaces:

- **Internal console** at `/:tenant/app/chat` (end-user app): inbox with *My conversations*
  / *Queue* / *All* tabs (the `ApprovalsInboxPage` pattern — filters + tabs + bell), a
  conversation view (virtualized message list, composer, attachment upload, presence
  "viewing" chip), claim/close actions, unread badges from `lastReadAt`.
- **Portal widget** `chat-panel` in the page-builder registry: a tenant drops it on a
  custom-app page; a portal user sees their conversations, starts one (configured queue),
  and messages the agent. Props: `queueId`, `welcomeText`, `allowAttachments`.
- Shared primitives in `@kelta/components`: `MessageList`, `MessageComposer`,
  `ConversationListItem` (console and widget both consume them — component-reuse rule).
- Realtime: `RealtimeClient` gains `joinConversation/leaveConversation` + `chat.message`
  event handling → **invalidate** `['chat-messages', conversationId]` +
  `['chat-conversations']` (invalidation-only rule holds; no cache writes). Offline sends
  ride the existing outbox (`SyncEngine`) with pending/failed states in the composer.
- `TopNavBar` bell aggregates unread chat count alongside approvals; nav menu-item path kind
  `/chat`.

Does NOT deliver: typing indicators, emoji/reactions, message edit/delete (v2), video
escalation button (slice 6 adds it into this console).

## 2. UI samples

Console: left rail conversation list (status + unread + last message time), center thread
(day dividers, sender grouping, attachment chips with presigned download), composer with
attach + send (Cmd+Enter), header with claim/close + presence chip. Widget: single-thread
panel sized to its page-builder container, "Start conversation" empty state with
`welcomeText`.

## 3. Data & API contracts

Consumes slice-2 REST + WS contracts verbatim (parent §Shared contracts). New FE types in
`kelta-ui/app/src/types/chat.ts` mirroring the collection shapes. Read receipts:
`POST .../read-receipt` fired on thread focus (debounced). Unread count hook
`useUnreadChatCount(userId)` = conversations where `lastMessageAt > lastReadAt`, polled +
realtime-invalidated.

## 4. DB migrations

None.

## 5. File-by-file code changes (sketch)

| Area | Files |
|------|-------|
| @kelta/components | `MessageList`, `MessageComposer`, `ConversationListItem` (+ exports, stories/tests) |
| kelta-ui app | `pages/app/ChatConsolePage/` (inbox + thread), `hooks/useConversations.ts` / `useChatMessages.ts` / `useUnreadChatCount.ts`, `realtime/RealtimeClient.ts` (+chat actions/events), `realtime/invalidation.ts` (+chat keys), `navTabs.ts` `/chat` path kind, `TopNavBar` bell aggregation, App.tsx lazy route |
| Page builder | `widgets/builtins/engagement.tsx` (`chat-panel` descriptor, registered in `widgets/builtins/index.ts`) |
| Offline | outbox handling for `POST /api/chat/**` (queue + retry surfaces already generic) |
| i18n | `chat.*` keys in `en.json` (+ fallbacks) |

All new strings through `useI18n()`; pages `React.lazy`; reuse `Badge`, `Card`,
`DropdownMenu`, `apiClient`, `usePresence`.

## 6. Test plan

- Vitest: hooks (pagination, unread math, invalidation on chat.message), composer
  offline-queue state, widget renders from resolved props, RealtimeClient join/leave +
  resubscribe-on-reconnect (fake WebSocket).
- Playwright (post-deploy, skip-gated): two-context roundtrip — portal user (widget on a
  custom page) sends; agent console receives (via invalidation refetch), replies; portal
  sees reply; attachment upload/download; claim + close flow.
- Coverage: kelta-web 80% gate applies to the new components.

## 7. Docs to update (same PR)

`status.md` (chat 🟡 → ✅ end-to-end with slice 2); `playbooks.md` widget row if the
`engagement` category is new; `conventions.md` unchanged (invalidation rule already
documented in slice 2's update).

## 8. Risks & open questions

- Refetch-per-message latency (~1 RTT after invalidation) is the accepted v1 trade — if UX
  demands instant echo, local-echo of *own* sends (optimistic append of the POST response)
  is allowed since it's authorized data, not socket data.
- Widget inside arbitrary tenant pages must not double-mount the realtime provider —
  `CustomPage` runs inside `EndUserShell` (provider present); portal shell must too
  (verify in slice 1's portal landing work).
- Bell aggregation ordering with approvals count — keep two counters side-by-side rather
  than a merged number if design review objects.
