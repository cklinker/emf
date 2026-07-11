import { test, expect } from "@playwright/test";

/**
 * Telehealth scheduling (slice 4) — post-deploy spec, skip-gated until slices
 * 1–4 are live (approvals-inbox precedent). Needs a provider with availability
 * rules, a portal user (magic-link auth state), and a custom page carrying the
 * appointment-scheduler widget.
 *
 * Manual smoke (documented in specs/telehealth/4-scheduling.md):
 *  1. Seed availability for a provider (admin, telehealth-availability rows).
 *  2. Portal user opens the page with the scheduler → picks provider, slot,
 *     confirms → mailpit shows "appointment confirmed" with an .ics attachment
 *     and a visit link.
 *  3. Clicking the visit link in a fresh browser lands SIGNED IN on /app.
 *  4. Booking the same slot again fails with "no longer available".
 *  5. Provider sees the appointment on /app/appointments; Complete moves it.
 *  6. One hour before start, the reminder email arrives (sweep; shorten
 *     kelta.telehealth.reminders.offset-minutes in dev to test).
 */
test.describe.skip("Appointment scheduling (post-deploy)", () => {
  const base = process.env.KELTA_APP_URL ?? "http://localhost:5173";
  const tenant = process.env.KELTA_TENANT_SLUG ?? "acme";

  test("portal user books a slot from the scheduler widget", async ({
    browser,
  }) => {
    const portalContext = await browser.newContext({
      storageState: "playwright/.auth/portal.json",
    });
    const portal = await portalContext.newPage();

    await portal.goto(`${base}/${tenant}/app/p/book-a-visit`);
    await portal
      .getByTestId("appointment-scheduler-provider")
      .selectOption({ index: 1 });
    await portal
      .locator('[data-testid^="appointment-scheduler-slot-"]')
      .first()
      .click();
    await portal.getByTestId("appointment-scheduler-confirm").click();

    await expect(
      portal.getByText("Booked! A confirmation email is on its way."),
    ).toBeVisible();

    await portalContext.close();
  });

  test("provider works the schedule on /app/appointments", async ({ page }) => {
    await page.goto(`${base}/${tenant}/app/appointments`);
    await expect(page.getByText("My Appointments")).toBeVisible();
  });
});
