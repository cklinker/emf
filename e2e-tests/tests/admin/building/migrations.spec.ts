import { test, expect } from "../../../fixtures";

test.describe("Migrations", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/migrations");
    await page.waitForLoadState("networkidle");
  });

  test("displays migrations page", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /migration/i });
    await expect(heading).toBeVisible();
  });

  test("shows migration history section", async ({ page }) => {
    // The page shows a "Migration History" heading and either a table or empty message
    const historyHeading = page.getByText(/migration history/i);
    const noHistory = page.getByText(/no migration/i);
    const table = page.locator("table");
    const historyOrEmpty = historyHeading.or(noHistory).or(table);
    await expect(historyOrEmpty).toBeVisible();
  });

  test("has plan migration button", async ({ page }) => {
    const planButton = page.getByRole("button", {
      name: /plan migration/i,
    });
    await expect(planButton).toBeVisible();
  });
});
