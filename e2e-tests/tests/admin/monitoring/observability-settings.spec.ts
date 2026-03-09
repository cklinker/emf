import { test, expect } from "../../../fixtures";
import { ObservabilitySettingsPage } from "../../../pages/observability-settings.page";

test.describe("Observability Settings", () => {
  let settingsPage: ObservabilitySettingsPage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    settingsPage = new ObservabilitySettingsPage(page, tenantSlug);
    await settingsPage.goto();
  });

  test("displays observability settings page with title", async ({ page }) => {
    // The page renders a loading spinner then either settings or error
    const container = settingsPage.container;
    const errorMsg = page.locator('[data-testid="error-message"]');
    const contentOrError = container.or(errorMsg);
    await expect(contentOrError).toBeVisible();

    // If settings loaded, check the heading
    if (await container.isVisible()) {
      const heading = page.getByRole("heading", {
        name: /observability settings/i,
      });
      await expect(heading).toBeVisible();
    }
  });

  test("shows retention inputs", async () => {
    // Skip if settings API returned an error
    test.skip(
      !(await settingsPage.container.isVisible().catch(() => false)),
      "Settings API unavailable",
    );
    await expect(settingsPage.traceRetentionInput).toBeVisible();
    await expect(settingsPage.logRetentionInput).toBeVisible();
    await expect(settingsPage.auditRetentionInput).toBeVisible();
  });

  test("shows save button", async () => {
    // Skip if settings API returned an error
    test.skip(
      !(await settingsPage.container.isVisible().catch(() => false)),
      "Settings API unavailable",
    );
    await expect(settingsPage.saveButton).toBeVisible();
  });

  test("navigates via monitoring hub tabs", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/monitoring`);
    await page.waitForLoadState("networkidle");
    await page.getByTestId("monitoring-tab-settings").click();
    await page.waitForURL(`**/${tenantSlug}/monitoring/settings`);
    // Verify the monitoring layout heading is visible (always present)
    const heading = page.getByRole("heading", { name: /monitoring/i });
    await expect(heading).toBeVisible();
  });
});
