import { test, expect } from "../../../fixtures";
import { DashboardsPage } from "../../../pages/dashboards.page";

test.describe("Dashboards", () => {
  test("displays dashboards page", async ({ page }) => {
    const dashboardsPage = new DashboardsPage(page);
    await dashboardsPage.goto();

    await expect(page).toHaveURL(/\/dashboards/);
    await expect(dashboardsPage.dashboardsPage).toBeVisible();
  });

  test("has create dashboard button", async ({ page }) => {
    const dashboardsPage = new DashboardsPage(page);
    await dashboardsPage.goto();
    await page.waitForLoadState("networkidle");

    await expect(dashboardsPage.createButton).toBeVisible();
  });

  test("shows dashboards list or empty state", async ({ page }) => {
    const dashboardsPage = new DashboardsPage(page);
    await dashboardsPage.goto();

    const listOrEmpty = dashboardsPage.dashboardList.or(
      page.getByTestId("empty-state"),
    );
    await expect(listOrEmpty).toBeVisible();
  });
});
