import { test, expect } from "../../../fixtures";
import { ObservabilitySettingsPage } from "../../../pages/observability-settings.page";

test.describe("Observability Settings", () => {
  let settingsPage: ObservabilitySettingsPage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    settingsPage = new ObservabilitySettingsPage(page, tenantSlug);
    await settingsPage.goto();
  });

  test("displays observability settings page with title", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /observability settings/i });
    await expect(heading).toBeVisible();
  });

  test("shows retention inputs", async () => {
    await expect(settingsPage.traceRetentionInput).toBeVisible();
    await expect(settingsPage.logRetentionInput).toBeVisible();
    await expect(settingsPage.auditRetentionInput).toBeVisible();
  });

  test("shows save button", async () => {
    await expect(settingsPage.saveButton).toBeVisible();
  });

  test("navigates from setup page", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/setup`);
    await page.waitForLoadState("networkidle");
    await page.getByText("Observability Settings", { exact: true }).click();
    await page.waitForURL(`**/${tenantSlug}/observability-settings`);
    const heading = page.getByRole("heading", { name: /observability settings/i });
    await expect(heading).toBeVisible();
  });
});
