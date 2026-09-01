# AWS / DNS setup for the SpotOpened support mailbox

Provisioned 2026-09-01. **None of this is in IaC** — the account has no Terraform and DNS is managed imperatively, so this file is the record. Recreate from here if the account is ever rebuilt.

| Thing | Value |
|---|---|
| Account | `138073884482` |
| Region | `us-east-1` — chosen because the verified SES identity already lived there, it has production access, and it supports SES email receiving (`us-east-2`, the house region elsewhere, is still sandboxed here and had no identity) |
| Route53 zone | `Z042777132CA8T9R9TK6L` (`spotopened.com`) |
| SES sending | Production access, 50k/24h, 14/sec |
| Local CLI profile | `rzware` |

## DNS records

| Name | Type | Value |
|---|---|---|
| `spotopened.com` | MX | `10 inbound-smtp.us-east-1.amazonaws.com` |
| `spotopened.com` | TXT | `v=spf1 include:amazonses.com ~all` **plus** the pre-existing `google-site-verification=…` |
| `_dmarc.spotopened.com` | TXT | `v=DMARC1; p=none; rua=mailto:dmarc@spotopened.com; fo=1` |
| `mail.spotopened.com` | MX | `10 feedback-smtp.us-east-1.amazonses.com` |
| `mail.spotopened.com` | TXT | `v=spf1 include:amazonses.com ~all` |
| `*._domainkey.spotopened.com` | CNAME | three Easy DKIM records, pre-existing |

The apex TXT holds **two** values. A Route53 `UPSERT` replaces every value in a record set, so any future edit must carry the Google verification string through or it is silently deleted.

`p=none` is deliberate: it is a monitoring posture. Read the `rua` reports before tightening to `quarantine`, or a mis-set record starts discarding real mail at receivers.

`~all` rather than `-all` for the same reason. SES is currently the only sender for the domain, so `-all` is defensible later.

## Receiving

- Bucket `spotopened-inbound`, public access blocked, SSE-S3, TLS-only bucket policy, **30-day lifecycle on `inbound/`**. This is a spool, not an archive — the durable copy will live in Garage once ingest exists.
- Bucket policy allows `ses.amazonaws.com` `s3:PutObject` only, conditioned on `AWS:SourceAccount` and a `receipt-rule-set/*` `SourceArn`.
- Receipt rule set `spotopened-inbound`, **active**, one rule `spotopened-catch-all`:
  - Recipients: `spotopened.com` — the whole domain, not named addresses. Customers mistype, DMARC reports go to `dmarc@`, and new addresses should not require an AWS change. With a 30-day lifecycle the cost of catching junk is nil, and the cost of *not* catching a real message is a lost customer.
  - `ScanEnabled: true` → SES writes SPF/DKIM/DMARC/spam/virus verdicts into the stored message.
  - `TlsPolicy: Optional` — `Require` rejects senders without TLS, which drops legitimate mail.
  - One `S3Action` carrying a `TopicArn`. **Not a separate `SNSAction`**: a bare SNS action caps at ~150 KB after base64 and SES truncates silently, so one screenshot would cost you the message body. The S3 action's notification carries the object key instead, and the reader fetches the full MIME from S3.

## SNS topics

| Topic | Subscription | Purpose |
|---|---|---|
| `spotopened-ses-notifications` | `https://api.kelta.io/api/webhooks/ses` (confirmed) | Bounce + complaint → `email_suppression`. Pre-existing. |
| `spotopened-inbound` | **none yet** | Inbound notification. Deliberately unsubscribed until `POST /api/webhooks/mail/{mailboxKey}` exists (slice 3). |

When slice 3 lands, subscribe the second topic to that URL and pin its ARN on the `mailbox` row's `provider_topic_arn`. A valid SNS signature only proves *some* topic signed the message; the ARN pin is what makes it ours.

## Verified end to end

Sent `noreply@spotopened.com` → `support@spotopened.com` twice, before and after the MAIL FROM change, and read the stored object back:

- Message lands in `s3://spotopened-inbound/inbound/` within seconds.
- Verdicts written: `spf=pass`, `dkim=pass`, `dmarc=pass`.
- DKIM signs with `d=spotopened.com` — aligned.
- After the MAIL FROM change the envelope is `@mail.spotopened.com`, so SPF is aligned too. DMARC now passes on **both** mechanisms rather than DKIM alone, which matters when a message is relayed and one of them breaks.

## Known gaps

- **The account is using root access keys** (`arn:aws:iam::138073884482:root`). Everything above was done with them. Replace with a scoped IAM user or role — AWS treats root keys as an anti-pattern precisely because they cannot be scoped or cleanly revoked.
- Inbound is a catch-all with no consumer yet. Mail accumulates in S3 and ages out after 30 days. Nothing reads it until slice 3, and nobody is notified it arrived — **if a customer emails support today, no human finds out**. That is still strictly better than the previous state, where the message was rejected at SMTP and lost entirely, but it is not "handled".
- No AV scanning of attachments anywhere in the platform; SES's `virusVerdict` is the only signal and it is not yet read.
- `homelab-argo/ddns-updater` maintains only `rzware.com`, so the `spotopened.com` A records still go stale if the home IP changes. Mail is now unaffected by that — MX points at SES, not at the house.
