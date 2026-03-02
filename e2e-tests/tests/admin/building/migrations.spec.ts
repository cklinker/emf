import { test, expect } from "../../../fixtures";

test.describe("Migrations", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/default/migrations");
    await page.waitForLoadState("networkidle");
  });

  test("displays migrations page", async ({ page }) => {
    // Page has both h1 "Migrations" and h2 "Migration History" — target h1 specifically
    const heading = page.getByRole("heading", { name: /^Migrations$/i });
    await expect(heading.first()).toBeVisible();
  });

  test("shows migration history section", async ({ page }) => {
    // The page shows a "Migration History" h2 heading and either a table or empty message
    const historyHeading = page.getByRole("heading", {
      name: /migration history/i,
    });
    await expect(historyHeading).toBeVisible();
  });

  test("has plan migration button", async ({ page }) => {
    const planButton = page.getByRole("button", {
      name: /plan migration/i,
    });
    await expect(planButton).toBeVisible();
  });
});
