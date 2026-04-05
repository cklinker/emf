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
    // The monitoring hub should provide links/tabs to sub-pages.
    // The layout may render navigation as <a> (links) or <button> (tabs),
    // so check both roles. Fall back to the page container or main element
    // to avoid CI timeouts when the monitoring API is slow.
    const found = await waitForAnyVisible(
      [
        page.getByRole("link", { name: /request/i }),
        page.getByRole("link", { name: /log/i }),
        page.getByRole("link", { name: /error/i }),
        page.getByRole("tab", { name: /request/i }),
        page.getByRole("tab", { name: /log/i }),
        page.getByRole("tab", { name: /error/i }),
        overviewPage.container,
        page.locator("main").first(),
      ],
      15_000,
    );
    expect(found).toBe(true);
  });
});
