import { test, expect } from "@playwright/test";

/**
 * Portal magic-link login (telehealth slice 1) — post-deploy spec.
 *
 * Skip-gated until the slice is live on the dev stack (per the
 * page-builder-v2.spec.ts precedent): the flow needs mailpit to capture the
 * magic link, and an admin-issued portal invite to exist.
 *
 * Manual smoke (documented in specs/telehealth/1-portal-identity.md):
 *  1. Admin → Users → Invite Portal User (patient@example.com)
 *  2. Open mailpit (:8025), click "Open your portal" — lands signed-in in /app
 *  3. Sign out; /portal/login?tenant=<slug> → request link → mailpit → signed in
 *  4. Same link again → "invalid, expired, or already used"
 */
test.describe.skip("Portal magic-link login (post-deploy)", () => {
  const authBase = process.env.KELTA_AUTH_URL ?? "http://localhost:9000";
  const mailpit = process.env.MAILPIT_URL ?? "http://localhost:8025";
  const tenant = process.env.KELTA_TENANT_SLUG ?? "acme";

  test("requesting a link renders the uniform confirmation for any email", async ({
    page,
  }) => {
    await page.goto(`${authBase}/portal/login?tenant=${tenant}`);
    await expect(page.getByText("Portal sign in")).toBeVisible();

    await page.fill("#email", "definitely-not-a-user@example.com");
    await page.click('button[type="submit"]');

    // Enumeration-safe: same response whether or not the email matched.
    await expect(page.getByText("Check your email")).toBeVisible();
  });

  test("a portal user signs in via the emailed link, once", async ({
    page,
    request,
  }) => {
    await page.goto(`${authBase}/portal/login?tenant=${tenant}`);
    await page.fill("#email", "portal-e2e@example.com");
    await page.click('button[type="submit"]');
    await expect(page.getByText("Check your email")).toBeVisible();

    // Pull the newest message for the address from mailpit and extract the link.
    const search = await request.get(
      `${mailpit}/api/v1/search?query=to:portal-e2e@example.com&limit=1`,
    );
    const { messages } = await search.json();
    expect(messages.length).toBeGreaterThan(0);
    const message = await (
      await request.get(`${mailpit}/api/v1/message/${messages[0].ID}`)
    ).json();
    const link = message.Text.match(
      /https?:\/\/\S*\/portal\/login\/verify\?token=\S+/,
    )?.[0];
    expect(link).toBeTruthy();

    await page.goto(link!);
    await expect(page).toHaveURL(new RegExp(`/${tenant}/app`));

    // Single use: the same link now lands on the error page.
    await page.context().clearCookies();
    await page.goto(link!);
    await expect(
      page.getByText("invalid, expired, or was already used"),
    ).toBeVisible();
  });
});
