import { test, expect } from "../../../fixtures";
import { ReportsPage } from "../../../pages/reports.page";

test.describe("Reports", () => {
  test("displays reports page", async ({ page }) => {
    const reportsPage = new ReportsPage(page);
    await reportsPage.goto();

    await expect(page).toHaveURL(/\/reports/);
    await expect(reportsPage.reportsPage).toBeVisible();
  });

  test("has create report button", async ({ page }) => {
    const reportsPage = new ReportsPage(page);
    await reportsPage.goto();
    await page.waitForLoadState("networkidle");

    await expect(reportsPage.createButton).toBeVisible();
  });

  test("shows reports list or empty state", async ({ page }) => {
    const reportsPage = new ReportsPage(page);
    await reportsPage.goto();

    const listOrEmpty = reportsPage.reportList.or(
      page.getByTestId("empty-state"),
    );
    await expect(listOrEmpty).toBeVisible();
  });
});
