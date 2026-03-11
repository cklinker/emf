import { test, expect } from "../../../fixtures";
import { EndpointPerformancePage } from "../../../pages/endpoint-performance.page";

test.describe("Endpoint Performance", () => {
  let perfPage: EndpointPerformancePage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    perfPage = new EndpointPerformancePage(page, tenantSlug);
    await perfPage.goto();
  });

  test("displays endpoint performance page with title", async ({ page }) => {
    const heading = page.getByRole("heading", {
      name: /endpoint performance/i,
    });
    await expect(heading).toBeVisible();
  });

  test("shows performance table or empty state", async ({ page }) => {
    const table = perfPage.leaderboard;
    const noData = page.getByText(/no endpoint performance data/i);
    const tableOrEmpty = table.or(noData);
    await expect(tableOrEmpty).toBeVisible();
  });

  test("shows latency percentile columns", async ({ page }) => {
    const table = perfPage.leaderboard;
    if (await table.isVisible()) {
      await expect(page.getByText("P50")).toBeVisible();
      await expect(page.getByText("P95")).toBeVisible();
      await expect(page.getByText("P99")).toBeVisible();
    }
  });

  test("navigates via monitoring hub tabs", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/monitoring`);
    await page.waitForLoadState("load");
    await page.getByTestId("monitoring-tab-performance").click();
    await page.waitForURL(`**/${tenantSlug}/monitoring/performance`);
    const heading = page.getByRole("heading", {
      name: /endpoint performance/i,
    });
    await expect(heading).toBeVisible();
  });
});
