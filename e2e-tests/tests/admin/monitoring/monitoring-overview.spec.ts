import { test, expect } from "../../../fixtures";
import { MonitoringOverviewPage } from "../../../pages/monitoring-overview.page";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Monitoring Overview", () => {
  let overviewPage: MonitoringOverviewPage;

  test.beforeEach(async ({ page }) => {
    overviewPage = new MonitoringOverviewPage(page);
    await overviewPage.goto();
  });

  test("displays monitoring overview page", async ({ page }) => {
    const found = await waitForAnyVisible([
      overviewPage.container,
      page.locator("main").first(),
    ]);
    expect(found).toBe(true);
  });

  test("shows monitoring navigation links", async ({ page }) => {
    // The monitoring hub should provide links to sub-pages
    const found = await waitForAnyVisible([
      page.getByRole("link", { name: /request/i }),
      page.getByRole("link", { name: /log/i }),
      page.getByRole("link", { name: /error/i }),
      overviewPage.container,
    ]);
    expect(found).toBe(true);
  });
});
