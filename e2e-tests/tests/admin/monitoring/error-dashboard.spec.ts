import { test, expect } from "../../../fixtures";
import { ErrorDashboardPage } from "../../../pages/error-dashboard.page";

test.describe("Error Dashboard", () => {
  let errorDashboard: ErrorDashboardPage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    errorDashboard = new ErrorDashboardPage(page, tenantSlug);
    await errorDashboard.goto();
  });

  test("displays error dashboard page with title", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /error dashboard/i });
    await expect(heading).toBeVisible();
  });

  test("shows error summary cards", async ({ page }) => {
    await expect(page.getByText(/error rate/i)).toBeVisible();
    await expect(page.getByText(/total errors/i)).toBeVisible();
  });

  test("shows error table or empty state", async ({ page }) => {
    const table = errorDashboard.topErrorsTable;
    const noErrors = page.getByText(/no errors found/i);
    const tableOrEmpty = table.or(noErrors);
    await expect(tableOrEmpty).toBeVisible();
  });

  test("navigates via monitoring hub tabs", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/monitoring`);
    await page.waitForLoadState("networkidle");
    await page.getByTestId("monitoring-tab-errors").click();
    await page.waitForURL(`**/${tenantSlug}/monitoring/errors`);
    const heading = page.getByRole("heading", { name: /error dashboard/i });
    await expect(heading).toBeVisible();
  });
});
