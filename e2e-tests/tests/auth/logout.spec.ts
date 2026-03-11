import { test, expect } from "../../fixtures";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Logout", () => {
  // Skip all logout tests: The OIDC logout flow goes through Authentik's
  // end-session endpoint, which involves external redirects and session
  // invalidation that is difficult to reliably test in e2e without a
  // dedicated Authentik test harness. These tests will be re-enabled once
  // we have a stable approach for handling the full OIDC logout round-trip.

  test.skip("can logout from the application", async ({ page }) => {
    // This test requires clicking the logout button, which triggers a
    // redirect to Authentik's end-session endpoint, then back to the app.
    // The multi-step OIDC logout redirect is unreliable in e2e tests.
    await page.goto(`/${tenantSlug}/collections`);
    await page.waitForLoadState("load");

    const logoutButton = page.getByTestId("logout-button");
    await logoutButton.click();

    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/login`));
  });

  test.skip("redirects to login after logout", async ({ page }) => {
    // Skipped: OIDC logout flows through Authentik's end-session endpoint.
    // The redirect chain (app -> Authentik -> app/login) is unreliable in e2e.
    await page.goto(`/${tenantSlug}/collections`);
    await page.waitForLoadState("load");

    const logoutButton = page.getByTestId("logout-button");
    await logoutButton.click();

    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/login`));
  });

  test.skip("cannot access protected pages after logout", async ({ page }) => {
    // Skipped: Requires completing the full OIDC logout flow first, then
    // verifying session invalidation. The Authentik round-trip makes this
    // flaky without a dedicated test harness.
    await page.goto(`/${tenantSlug}/collections`);
    await page.waitForLoadState("load");

    const logoutButton = page.getByTestId("logout-button");
    await logoutButton.click();

    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/login`));

    await page.goto(`/${tenantSlug}/collections`);

    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/login`));
  });
});
