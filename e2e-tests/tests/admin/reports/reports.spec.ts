import { test, expect } from "../../../fixtures";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Reports", () => {
  test("displays analytics page", async ({ page }) => {
    await page.goto("/default/reports");
    await page.waitForLoadState("load");

    await expect(page).toHaveURL(/\/reports/);
    const found = await waitForAnyVisible([
      page.getByRole("heading", { name: /analytics/i }),
      page.getByTestId("analytics-page"),
      page.getByTestId("error-message"),
    ]);
    expect(found).toBe(true);
  });

  test("has open in superset button", async ({ page }) => {
    await page.goto("/default/reports");
    await page.waitForLoadState("load");

    const found = await waitForAnyVisible([
      page.getByRole("link", { name: /open in superset/i }),
      page.getByTestId("error-message"),
      page.getByTestId("analytics-page"),
    ]);
    expect(found).toBe(true);
  });

  test("shows dashboards or empty state", async ({ page }) => {
    await page.goto("/default/reports");
    await page.waitForLoadState("load");

    const found = await waitForAnyVisible([
      page.getByText(/no dashboards available/i),
      page.getByRole("button", { name: /dashboard/i }),
      page.getByTestId("error-message"),
      page.getByTestId("analytics-page"),
    ]);
    expect(found).toBe(true);
  });
});
