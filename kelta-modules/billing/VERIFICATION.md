# Live verification runbook — billing module

Verifies the billing module end-to-end against **real Stripe test-mode keys** on the live
`spotopened` tenant. Until this passes, the compiled-in `kelta-worker` billing stays in place.

**Steps marked 🔑 require you to handle secrets and must not be delegated to an agent.**

Environment established by inspection, not assumption:

| | |
|---|---|
| Tenant | `spotopened` = `350a7dfe-3cb4-45ea-8816-555bd04c505e` |
| API | `https://api.kelta.io` (CLI profile `spotopened`) |
| Signing | **required** (`KELTA_MODULES_SIGNING_REQUIRED=true`), no platform key |
| Tenant key | Ed25519, label `2026-h2`, fp `7d796bd4…`, active, 1 dependent module |
| Precedent | `spotopened-ridb` v0.1.0 is installed and ACTIVE — this path is proven |

## ⚠️ The failure mode to watch for

A signature or checksum failure **does not fail the install**. The module falls back to inert stub
handlers and still reports `status: ACTIVE`. Every action then returns `"mode": "stub"` and quietly
does nothing.

**Never treat ACTIVE as working.** Step 4 is the real gate.

---

## 0. Prerequisite — land the tenant-binding fix first

The webhook route binds no tenant of its own until PR #1381 ships. Without it the module's writes
run under the `admin_bypass` RLS policy, unscoped across tenants — verification would "pass" while
writing in the wrong scope.

```bash
kelta api GET /api/modules | grep -q uiBundlePath && echo "worker has #1378+ ✓"
```

Confirm the deployed worker tag is at or past `main-01cf091` (the merge of #1381):

```bash
kubectl get pods -n emf -o custom-columns='N:.metadata.name,I:.spec.containers[0].image' --no-headers | grep worker
```

✅ Satisfied as of 2026-08-30: worker pods run `main-01cf091`, rolled 01:25Z.

## 1. Build the module

```bash
mvn package -f kelta-modules/billing/pom.xml
shasum -a 256 kelta-modules/billing/target/kelta-module-billing-1.0.0.jar
```

Last known-good build, from `01cf0911` (the merge of #1381):
`2ea8a8a8f81b513c83833977252fa583d94b30cbbc41c6ac6983d3b42c8a8c8c`
Re-derive it rather than trusting this line — the JAR changes whenever the module does.

Sanity-check the JAR before signing something incomplete:

```bash
unzip -l "$JAR" | grep -E 'kelta-module.json|ui-bundle.js|BillingModule.class'
unzip -p "$JAR" kelta-module.json | python3 -c "import sys,json;m=json.load(sys.stdin);print(len(m['actionHandlers']),'handlers',len(m['collections']),'collections')"
# expect: all three present, and "5 handlers 5 collections"
```

## 2. 🔑 Sign the JAR

Use the **same Ed25519 private key** whose public half is registered as `2026-h2` — the one that
signed `spotopened-ridb`. A different key installs as stubs (see the warning above).

```bash
JAR=kelta-modules/billing/target/kelta-module-billing-1.0.0.jar
openssl pkeyutl -sign -rawin -inkey /path/to/your/module-signing-key.pem -in "$JAR" \
  | base64 | tr -d '\n' > /tmp/billing.sig
```

`tr -d '\n'` is not optional. The verifier uses Java's **basic** Base64 decoder, which rejects
embedded newlines, and `base64` wraps at 76 columns on Linux (macOS does not). Without it the
install fails on some machines and not others.

Keep the private key out of the repo and out of any agent session.

## 3. Install

The CLI has no `modules` command and `kelta api` cannot send multipart, so this one call is raw
curl and needs a PAT of its own. 🔑 Mint one and keep it in your shell only:

```bash
kelta token create --name billing-install   # full token is shown exactly once
export KELTA_PAT='klt_...'                  # paste it here, not into a file
```

```bash
curl -sS -X POST https://api.kelta.io/api/modules/install-jar \
  -H "Authorization: Bearer $KELTA_PAT" \
  -H "X-Tenant-Slug: spotopened" \
  -F "manifest=@kelta-modules/billing/src/main/resources/kelta-module.json" \
  -F "jar=@kelta-modules/billing/target/kelta-module-billing-1.0.0.jar" \
  -F "signature=$(cat /tmp/billing.sig)"
```

Revoke it when you are done: `kelta token list` then `kelta token revoke <id>`.

A `403` means the signature did not verify — fix it here rather than continuing, or you get stubs.

Then enable it:

```bash
kelta api POST /api/modules/kelta-billing/enable
```

## 4. 🚨 Gate: prove it is NOT running as stubs

An action handler is not callable on its own — it is a Task node's executable unit, so it runs via
a flow. Create a one-step probe flow once and reuse it:

```bash
kelta flows create --name billing-probe --definition '{
  "StartAt": "Resolve",
  "States": { "Resolve": { "Type": "Task", "Resource": "billing:resolve-entitlements", "End": true } }
}'
kelta flows execute <flowId>
kelta flows runs <flowId>          # then: kelta flows run <executionId> --steps
```

Read the step output:

- `"mode": "stub"` ⇒ **the JAR did not verify.** Stop and re-check step 2. The module will still
  say ACTIVE — that is the trap this step exists to catch.
- A real result (`planCode`/`entitlements`, or `No calling member` when run without an actor) ⇒
  real module code is loaded.

Also confirm the five collections were provisioned:

```bash
kelta api GET /api/collections | grep -o 'billing_[a-z_]*' | sort -u
# expect: billing_customers billing_entitlement_rules billing_passes billing_plans billing_subscriptions
```

## 5. 🔑 Create the Stripe credential

In the admin UI (**Setup → Credentials → New → type `stripe`**, name **exactly** `stripe`):

| Field | Value |
|---|---|
| `secretKey` | your Stripe **test-mode** `sk_test_…` |
| `webhookSecret` | the endpoint signing secret from step 6 (`whsec_…`) |
| `allowedReturnOrigins` | e.g. `https://app.spotopened.com` |

Enter these yourself in the UI — never paste them into a terminal, a file, or an agent session.
Use **test mode** for verification; a real charge is not required to prove the path.

`allowedReturnOrigins` is enforced on scheme + host + port. Omit it and every checkout is rejected
(fail-closed, by design).

## 6. 🔑 Point Stripe at the webhook

Stripe Dashboard → Developers → Webhooks → Add endpoint:

```
https://api.kelta.io/api/modules/webhooks/350a7dfe-3cb4-45ea-8816-555bd04c505e/kelta-billing
```

Events: `checkout.session.completed`, `customer.subscription.created`,
`customer.subscription.updated`, `customer.subscription.deleted`.

Copy the signing secret back into the credential's `webhookSecret` (step 5).

Sanity-check the negative — an unsigned request must never be accepted:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  https://api.kelta.io/api/modules/webhooks/350a7dfe-3cb4-45ea-8816-555bd04c505e/kelta-billing \
  -H 'Content-Type: application/json' -d '{}'
```

| Response | Meaning |
|---|---|
| **404** | Nothing to dispatch to — module not installed or not ACTIVE. This is the expected answer *before* step 3, and was confirmed live on 2026-08-29. |
| **401** | The module received it and rejected the signature. Expected *after* install. |
| **2xx** | 🚨 Signature verification is not happening. Stop. |

A `404` before install also proves the route is reachable **without authentication** — the gateway
is not rejecting it, the dispatcher is.

## 7. Seed plans

```bash
kelta api POST /api/billing_plans -d '{"code":"FREE","name":"Free","kind":"DEFAULT","active":true,"entitlements":{"watches":1}}'
kelta api POST /api/billing_plans -d '{"code":"PRO","name":"Pro","kind":"SUBSCRIPTION","active":true,"stripePriceId":"price_...","entitlements":{"watches":25}}'
```

`stripePriceId` must match a real test-mode price, or checkout returns "Plan is not available for
purchase".

## 8. Exercise the path

| Check | How | Expected |
|---|---|---|
| Checkout | `billing:create-checkout-session` with `planCode=PRO` + allowed URLs | a `checkout.stripe.com` URL |
| Pay | Stripe test card `4242 4242 4242 4242` | redirected to `successUrl` |
| Webhook | Stripe Dashboard → webhook attempts | `200`, not 401/404/500 |
| Mirror | `kelta api GET /api/billing_subscriptions` | one row, `status: active`, `currentPeriodEnd` **non-null** |
| Entitlements | `billing:resolve-entitlements` | `planCode: PRO`, `watches: 25` |
| Portal | `billing:create-portal-session` | a `billing.stripe.com` URL |
| Expiry | `billing:expire-passes` | `{"expired":0,"batchFull":false}` on a clean tenant |

**`currentPeriodEnd` is the one to look at closely.** Stripe moved it from the subscription onto
each item in `2025-03-31.basil`, and webhooks render with the *account's* API version, not the one
the client pins — so this field silently lands null if the payload shape differs from what the
parser expects. A null here is a real bug, not a cosmetic gap.

## 9. Quota hook

```bash
kelta api POST /api/billing_entitlement_rules \
  -d '{"collectionName":"watches","limitKey":"watches","appliesTo":"ALL","active":true,"message":"Upgrade to add more watches."}'
```

Create watches past the limit as a member — the create should be rejected with that message.

⚠️ Use `appliesTo: ALL`. **`PORTAL` rules are skipped entirely by the module** — it cannot read the
gateway's `X-User-Type` to tell staff from members. If you rely on `PORTAL` rules today, that is a
behaviour change to decide on before any cutover, not a detail.

## 10. Record the outcome

Update `status.md` and the module README either way. If it passes, the remaining cutover blocker is
the `PORTAL`-rule difference and the weaker webhook idempotency documented in the README — both
decisions, not bugs.

## Rollback

```bash
kelta api POST /api/modules/kelta-billing/disable   # unregisters handlers + hooks immediately
kelta api DELETE /api/modules/kelta-billing         # uninstall; collections and data are KEPT
```

Disable is enough to stop the module affecting anything: it removes the wildcard quota hook and
every action handler. Uninstall deliberately leaves the collections and their rows behind.
