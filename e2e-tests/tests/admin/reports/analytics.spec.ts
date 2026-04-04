import { test, expect } from "../../../fixtures";
import { AnalyticsPage } from "../../../pages/analytics.page";

test.describe("Analytics", () => {
  let analyticsPage: AnalyticsPage;

  test.beforeEach(async ({ page }) => {
    analyticsPage = new AnalyticsPage(page);
    await analyticsPage.goto();
  });

  test("displays analytics page", async () => {
    await expect(analyticsPage.container).toBeVisible();
  });

  test("shows analytics page content", async ({ page }) => {
    // The analytics page shows either dashboard cards or an empty state
    await expect(analyticsPage.container).toBeVisible();
    const pageText = await analyticsPage.container.textContent();
    expect(pageText).toBeTruthy();
  });
});
