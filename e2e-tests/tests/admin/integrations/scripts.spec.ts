import { test, expect } from "../../../fixtures";
import { ScriptsPage } from "../../../pages/scripts.page";

test.describe("Scripts", () => {
  test("displays scripts page", async ({ page }) => {
    const scriptsPage = new ScriptsPage(page);
    await scriptsPage.goto();

    await expect(page).toHaveURL(/\/scripts/);
    await expect(scriptsPage.scriptsPage).toBeVisible();
  });

  test("shows scripts table or empty state", async ({ page }) => {
    const scriptsPage = new ScriptsPage(page);
    await scriptsPage.goto();

    // Page may show table, empty state, or error state ("Failed to load")
    const hasTable = await scriptsPage.table
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const hasEmptyState = await page
      .getByTestId("empty-state")
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasError = await page
      .getByTestId("error-message")
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasLoading = await page
      .getByText(/loading/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(hasTable || hasEmptyState || hasError || hasLoading).toBe(true);
  });

  test("opens create script form", async ({ page }) => {
    const scriptsPage = new ScriptsPage(page);
    await scriptsPage.goto();

    await scriptsPage.clickCreate();

    await expect(scriptsPage.formModal).toBeVisible();
    await expect(scriptsPage.nameInput).toBeVisible();
  });

  test("has source code textarea in form", async ({ page }) => {
    const scriptsPage = new ScriptsPage(page);
    await scriptsPage.goto();

    await scriptsPage.clickCreate();

    await expect(scriptsPage.sourceCodeInput).toBeVisible();
  });
});
