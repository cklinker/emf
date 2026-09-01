# Support mailbox

A shared, SLA-tracked inbox that receives customer email, answers the routine share of it automatically, and routes everything else to a human.

Driven by SpotOpened, which publishes `support@spotopened.com` on three legal pages and `alerts@spotopened.com` to federal data providers in every scraper's User-Agent — and had no MX record, so both addresses rejected mail at the SMTP layer. Built as a platform feature rather than a tenant script because nothing about a shared mailbox is SpotOpened-specific.

## Shape

```
MX → SES receipt rule ─┬─ S3Action  → raw MIME (real AWS bucket, short lifecycle = spool)
                       └─ SNSAction → SNS topic
                                        │  signature verified, TopicArn pinned
                                        ▼
              POST /api/webhooks/mail/{mailboxKey}
                                        │
                    InboundMailAdapter (SES_SNS | GENERIC_HMAC | POSTMARK | …)
                                        │
                    mailbox_inbound_event  ← claim-before-parse; the INSERT is the idempotency
                                        │
                    MimeParser (jakarta.mail) → MailboxHtmlSanitizer (jsoup)
                                        │
                    MailboxThreadResolver → mailbox_thread / _message / _attachment
                                        │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
            SlaEscalationSweep                       SupportDraftSweep
            (@Scheduled, atomic claim)               (@Scheduled) ──▶ kelta-ai
                    │                                       │         allowed_tools = []
                    ▼                                       ▼
            escalation tree                    deterministic template match?
            (email / push / sms)                 ├─ yes + confident → auto-send rendered template
                                                 └─ no → mailbox_draft PENDING → human approves
```

Product API is `/api/support/**`. Ingest is `/api/webhooks/mail/**` — a **different top-level segment**, because `PublicPathMatcher` matches unauthenticated prefixes by `startsWith`, so an ingest path nested under the product path would mean a config trim silently opens the whole feature.

The closest existing analogue is chat (`V168__chat_backend.sql`, `ChatController`, `ChatConsolePage`), and this feature deliberately copies its schema spine, its "no `profile_object_permission` rows" access model, and its in-controller authorization. Three things differ, and they account for essentially all the added complexity: **the counterparty is unauthenticated and unverifiable**, **the message body is attacker-supplied HTML**, and **some replies are written by a model**.

## Decisions that are load-bearing

**Routing uses only the URL's `mailboxKey`.** `To:`, `Delivered-To:`, `X-Original-To:` and SES's `destination[]` are all attacker-controlled and are display-only. This is stricter than the existing `SesNotificationController`, which resolves a tenant from `mail.source` — safe there only because the SNS signature covers the payload, and not a pattern to generalise.

**The provider is a column on the mailbox row, never a URL segment.** Putting it in the URL would let anyone who learns a mailbox key choose which verifier runs, i.e. choose the weakest one.

**`webhook_key` is an identifier, not a secret.** It appears in provider consoles, access logs and support tickets. Authentication is separate and always required: SNS signature + pinned `TopicArn` for SES, HMAC over the raw body for generic providers, with the secret in the credential vault and two slots so rotation can overlap.

**Signature alone is not enough for SES.** A valid SNS signature proves "some topic in some AWS account signed this" — anyone can create a topic and publish to a public endpoint. The `TopicArn` pin is what makes it ours. `SesNotificationController` has this gap today and should be fixed alongside.

**SLA due times are frozen on the thread.** Computed once at thread creation and stored absolute. Deriving them at read from mailbox policy would mean an admin editing that field retroactively breaches or un-breaches every open thread and rewrites history in every report already run. It would also make the cross-tenant sweep unindexable.

**The drafting agent has `allowed_tools = []`.** This is the keystone of the never-disclose rule. A tool-less agent physically cannot read `watch`, `billing_subscription` or `platform_user`, so no prompt injection in an email body can make it. Product knowledge reaches the model by worker-side retrieval of curated template summaries — deterministic retrieval over a public-safe corpus, not model-chosen queries against production. It is also one seeded row and zero code in `kelta-ai`.

**The model does not choose the auto-send template.** If it did, "template-matched auto-send" would silently become "model-controlled send" and the human-approval requirement would be void. The matcher is deterministic; the model's category vote is one input to a confidence score, and agreement between the two classifiers is what buys an auto-send.

**Auto-send renders a human-authored template.** Enforced by signature rather than discipline: `autoSend(tenantId, messageId, templateKey)` has no parameter through which generated prose can arrive.

**Approval authority is per-mailbox membership, not a system permission.** A global "may approve" would apply to mailboxes the holder is not a member of.

## Never disclosing account data to an unverified sender

Six independent layers; any one can fail without account data leaving.

1. The drafting agent has no tools — it cannot read account data at all.
2. `autoSend` accepts no generated text.
3. Template variables are whitelisted at author time to platform constants — nothing derived from the inbound message. This matters because `DefaultEmailService.substitute()` does no HTML escaping, so any inbound-derived variable is an HTML injection into mail we send.
4. `dmarc_result != 'pass'` vetoes auto-send.
5. `mailbox_thread.requester_verified` defaults false, raised only by a DMARC-aligned identity match, a challenge sent to an address **already on the account record**, or an audited manual action. A challenge sent to the sender's own address proves only address control — which the attacker who registered `support-victim@gmail.com` also has.
6. Everything else is read by a human first.

A property worth preserving deliberately: because the auto-replier never looks the sender up, its reply to a real customer and to a stranger is byte-identical. That closes an account-enumeration oracle reachable by anyone with an SMTP client.

## Slices

| # | Contents | State |
|---|---|---|
| 0 | `EmailHeaders`, `SendResult`, `EmailProvider.sendAndReport`, `DefaultEmailService.queueReply` | **Shipped** (#1423) |
| 1 | `V191` schema, six system collections, two permissions | **Shipped** |
| 2 | `MailboxAdminController` (`/api/support/mailboxes`), repositories, secret minting/rotation, gateway static route | **Shipped** |
| 3 | Ingest: webhook, adapters, MIME parser, jsoup sanitizer, thread resolver, AWS-side raw-MIME store, per-IP budget | **Shipped** (SES_SNS + GENERIC_HMAC adapters; Postmark/Mailgun are one class each) |
| 4 | Console + SLA sweep + escalation dispatch | |
| 5 | Human replies (VERP `Reply-To`, suppression check, loop guards) | |
| 6 | `mailbox_template` — matching and policy, referencing `email_template` for copy | |
| 7 | Auto-send, shadow mode first | |
| 8 | AI drafts + approval UI, worker→`kelta-ai` client, `X-Internal-Token` on `/api/ai/agents/**` | |
| 9 | Attachment hardening; fix `FileViewer.tsx:131` (`sandbox="allow-same-origin"` on a same-origin URL) | |
| 10 | Cut MX from the interim Google Workspace alias over to SES | |

Slices 1–5 are shippable as an internal-only, human-reply-only mailbox. Slice 7 is the first that lets bytes leave without a human; slice 8 is the first that lets a model influence them. Both warrant their own review.

## Traps

- **`SEND_NOTIFICATION` is a logging stub.** It returns `status: SENT` and persists nothing, and there is no notification table anywhere. Escalations must not route through it. The in-app surface is a count query, not a notification store.
- **`AgentRuntimeService` silently drops `DispatchResult.proposal()`.** Persistence lives in `ChatService`, the chat path. The natural-looking "use a PROPOSE tool for drafts" design would lose every draft while reporting success.
- **`PiiMaskingService` masks tool results but not `userInput`** — deliberate, and this design depends on it. Its `PHONE`/`CREDIT_CARD` regexes eat any 10- or 16-digit run, so reservation and confirmation numbers would be destroyed. Do not "fix" the asymmetry for consistency.
- **`DefaultEmailService` does not check `email_suppression`;** only `CampaignRunnerService` does, despite `SesNotificationController`'s javadoc implying otherwise. The mailbox checks it explicitly. Folding it into `queueEmail` would silently start blocking password resets and invites — a real gap, but one needing its own change.
- **Never drop mail on SPF-fail alone.** Forwarding breaks SPF constantly and `.forward` breaks DKIM. Mark unverified and route to a human.
- **Never start an SLA clock on a `SPAM` thread** — escalation becomes a spam firehose paging someone at 3am.
- **An auto-reply that stops the SLA clock makes the dashboard gameable.** You can reach 100% by auto-replying "thanks for writing!" to everything. Ship the follow-up rate (auto-replied, then the customer wrote back within 72h) beside the SLA number.
- **`sandbox=""` on the body iframe must stay empty.** Auto-sizing needs `postMessage`, which needs `allow-scripts`; fixed height plus a full-message dialog is the correct trade. `allow-scripts allow-same-origin` together lets the frame remove its own sandbox attribute.
- **`MergeTagNode.ts:76-79` claims the backend sanitises with jsoup.** It does not today, and the comment becomes more dangerous once slice 3 adds jsoup for a different path. Fix it there.

## Two indexes are deliberately not tenant-leading

`mailbox_webhook_key_key`, because webhook resolution runs before a tenant is known — it is what determines the tenant. And the two partial SLA-sweep indexes, because the sweep runs cross-tenant on the scheduler thread with no tenant bound, riding `admin_bypass`. Both carry comments in `V191` saying so; they look like house-style violations and are not.
