# Kelta Billing Module

Stripe-backed portal billing, packaged as a **runtime-installable Kelta module**: one signed JAR
plus its manifest, uploaded into a tenant with no platform recompile or redeploy.

It is the reference implementation for the module SPI — the thing that proves a customer could
ship functionality Kelta never anticipated. Read it alongside
[`.claude/docs/playbooks.md`](../../.claude/docs/playbooks.md) → *Ship a runtime-installable module*.

## What is in the one JAR

| Piece | How it reaches the platform |
|-------|-----------------------------|
| `billing_plans`, `billing_customers`, `billing_subscriptions`, `billing_passes` | Declared in `kelta-module.json` → created at install through the standard collection API |
| `billing:create-checkout-session` | `ActionHandler`, invoked as a flow action |
| `billing:stripe-webhook` | `ActionHandler`, dispatched by `POST /api/modules/webhooks/{tenantId}/kelta-billing` |
| Billing Plans widget | `static/ui-bundle.js`, served by `GET /api/modules/kelta-billing/ui-bundle.js` |

## Build

```bash
mvn -f kelta-modules/billing/pom.xml package
```

Produces `target/kelta-module-billing-1.0.0.jar`. Install it through the admin UI's **Modules**
page, or `POST /api/modules/install-jar` with the JAR, `kelta-module.json`, and a detached
signature when the tenant has a signing key configured.

**This project is deliberately outside the `kelta-platform` Maven reactor and has no parent pom.**
Wiring it in would ship it with the platform and quietly undo the point of the exercise. Its
`runtime-core` / `runtime-module-integration` dependencies are `provided` because both load from
the platform classloader at runtime; bundling them would put a second copy of the runtime model in
the JAR and every cast to a platform type would fail.

## Tenant setup

1. Create a credential of type **`stripe`** named `stripe`, holding:
   - `secretKey` — `sk_…` or `rk_…`
   - `webhookSecret` — `whsec_…` (the endpoint signing secret)
   - `allowedReturnOrigins` — the origins checkout may return a member to
2. Point a Stripe webhook endpoint at
   `https://<tenant-host>/api/modules/webhooks/<tenantId>/kelta-billing`, subscribed to
   `checkout.session.completed`, `customer.subscription.created|updated|deleted`.
3. Create `billing_plans` rows. `kind` is `SUBSCRIPTION`, `ONE_TIME`, or `DEFAULT` (the free
   baseline — not purchasable). `stripePriceId` must match the Stripe price.

## Security notes

- **The webhook route is unauthenticated and the platform verifies nothing.** This module is the
  trust anchor: `ProcessStripeWebhookActionHandler` resolves the tenant's own credential and checks
  the HMAC over the **raw** body before parsing it. Re-serializing the body would change the bytes
  the signature covers.
- **Return URLs are matched on scheme + host + port**, never as a string prefix, and fail closed
  when no origins are configured — otherwise `https://good.example.com` would authorize
  `https://good.example.com.evil.test`.
- **The UI bundle runs same-origin** with the admin session's full DOM and cookie access. The JAR
  signature verified at install and re-verified on read is the entire trust model; there is no
  browser-side isolation.
- Stripe's own error messages are logged, never returned to a caller — they can name account
  internals. Money is never computed here: amounts, proration, tax, dunning, and retries all stay
  with Stripe, and these collections hold ids and coarse state only.

## Known limitations (versus the compiled-in implementation this replaces)

These are real gaps, not oversights — record them before relying on this in production:

- **Webhook idempotency is weaker.** The compiled-in version claims each event id in a row that
  shares one transaction with the mutation, so a failure rolls the claim back and Stripe's retry
  genuinely re-runs. A module writes through `QueryEngine` and has no transaction of its own, so
  this relies on convergence instead: a pass is granted once per checkout session, and a
  subscription is upserted by its Stripe id. Replaying converges; it does not duplicate. A partial
  failure mid-event can still leave one write applied and another not.
- **No entitlement resolution or quota enforcement yet.** `EntitlementService`,
  `MemberEntitlementQuotaHook`, and the pass-expiry sweep have not been ported. Until they are,
  installing this module does **not** replace the compiled-in billing in `kelta-worker`.
- **No portal-session action** (`billing:create-portal-session`) yet.
- **Live verification is owed.** Nothing here has run against real Stripe keys or a live tenant.
  The compiled-in billing code must stay in place until it has.
