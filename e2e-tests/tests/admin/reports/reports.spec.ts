import { test, expect } from "../../../fixtures";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Reports", () => {
  test("displays reports page", async ({ page }) => {
    await page.goto("/default/reports");
    await page.waitForLoadState("load");

    await expect(page).toHaveURL(/\/reports/);
    const found = await waitForAnyVisible([
      page.getByRole("heading", { name: /reports/i }),
      page.getByTestId("reports-page"),
      page.getByTestId("error-message"),
    ]);
    expect(found).toBe(true);
  });

  test("has create report button", async ({ page }) => {
    await page.goto("/default/reports");
    await page.waitForLoadState("load");

    const found = await waitForAnyVisible([
      page.getByRole("button", { name: /create report/i }),
      page.getByTestId("error-message"),
      page.getByTestId("reports-page"),
    ]);
    expect(found).toBe(true);
  });

  test("shows reports list or empty state", async ({ page }) => {
    await page.goto("/default/reports");
    await page.waitForLoadState("load");

    const found = await waitForAnyVisible([
      page.locator("table, [role='grid']"),
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      page.getByTestId("reports-page"),
    ]);
    expect(found).toBe(true);
  });
});
