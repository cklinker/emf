import { test, expect } from "../../../fixtures";
import { ObservabilitySettingsPage } from "../../../pages/observability-settings.page";

test.describe("Observability Settings", () => {
  let settingsPage: ObservabilitySettingsPage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    settingsPage = new ObservabilitySettingsPage(page, tenantSlug);
    await settingsPage.goto();
  });

  test("displays observability settings page with title", async ({ page }) => {
    const heading = page.getByRole("heading", {
      name: /observability settings/i,
    });
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

  test("navigates via monitoring hub tabs", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/monitoring`);
    await page.waitForLoadState("networkidle");
    await page.getByTestId("monitoring-tab-settings").click();
    await page.waitForURL(`**/${tenantSlug}/monitoring/settings`);
    const heading = page.getByRole("heading", {
      name: /observability settings/i,
    });
    await expect(heading).toBeVisible();
  });
});
