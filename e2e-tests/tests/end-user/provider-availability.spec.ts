import { test, expect } from "@playwright/test";

/**
 * Provider self-service availability (telehealth). Post-deploy spec, skip-gated
 * like the scheduling spec — it needs a live stack and a provider auth state
 * (an INTERNAL user who is a telehealth provider).
 *
 * Backend contract (server-enforced ownership): the page reads and writes
 * GET/PUT `/api/telehealth/availability/me`; the provider id comes from the
 * authenticated identity, never the body, so a provider can only edit their own
 * schedule. Unit coverage: AvailabilityServiceTest (worker) +
 * ProviderAvailabilityPage.test.tsx (ui).
 *
 * Manual / live smoke:
 *  1. Provider signs into the staff app, opens My appointments → "My availability".
 *  2. Adds a window to a weekday, Save → "Availability saved" toast.
 *  3. Reload → the window persists.
 *  4. Booking (portal) now offers slots inside the new window; removing the
 *     window and saving makes those slots disappear.
 */
test.describe.skip("Provider availability (post-deploy)", () => {
  const base = process.env.KELTA_APP_URL ?? "http://localhost:5173";
  const tenant = process.env.KELTA_TENANT_SLUG ?? "acme";

  test("provider edits and saves their weekly schedule", async ({ browser }) => {
    const context = await browser.newContext({
      storageState: "playwright/.auth/provider.json",
    });
    const page = await context.newPage();

    await page.goto(`${base}/${tenant}/app/provider-availability`);
    await expect(page.getByText("My availability")).toBeVisible();

    // Add a window to the first weekday that has an "Add hours" control.
    await page.getByRole("button", { name: /add hours/i }).first().click();
    await page.getByRole("button", { name: /save changes/i }).click();
    await expect(page.getByText("Availability saved")).toBeVisible();

    // The saved window survives a reload (persisted server-side).
    await page.reload();
    await expect(page.locator('input[type="time"]').first()).toHaveValue(/\d{2}:\d{2}/);

    await context.close();
  });
});
