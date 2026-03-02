import { test, expect } from "../../../fixtures";
import { SystemHealthPage } from "../../../pages/system-health.page";

test.describe("System Health", () => {
  let systemHealthPage: SystemHealthPage;

  test.beforeEach(async ({ page }) => {
    systemHealthPage = new SystemHealthPage(page);
    await systemHealthPage.goto();
  });

  test("displays system health dashboard", async () => {
    await expect(systemHealthPage.dashboardPage).toBeVisible();
  });

  test("shows health status cards", async ({ page }) => {
    const cardsOrEmpty = systemHealthPage.healthCards.or(
      page.getByTestId("empty-state"),
    );
    await expect(cardsOrEmpty).toBeVisible();
  });

  test("shows metrics cards (request rate, error rate, latency)", async ({
    page,
  }) => {
    const metricsOrEmpty = systemHealthPage.metricsCards.or(
      page.getByTestId("empty-state"),
    );
    await expect(metricsOrEmpty).toBeVisible();
  });

  test("has time range selector", async () => {
    await expect(systemHealthPage.timeRangeSelector).toBeVisible();
  });
});
