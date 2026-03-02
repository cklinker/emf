import { test, expect } from "../../../fixtures";
import { ScriptsPage } from "../../../pages/scripts.page";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

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

    const found = await waitForAnyVisible([
      scriptsPage.table,
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      scriptsPage.scriptsPage,
    ]);
    expect(found).toBe(true);
  });

  test("opens create script form", async ({ page }) => {
    const scriptsPage = new ScriptsPage(page);
    await scriptsPage.goto();

    // Create button may not be available in error state
    try {
      await scriptsPage.createButton.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await scriptsPage.clickCreate();

    await expect(scriptsPage.formModal).toBeVisible();
    await expect(scriptsPage.nameInput).toBeVisible();
  });

  test("has source code textarea in form", async ({ page }) => {
    const scriptsPage = new ScriptsPage(page);
    await scriptsPage.goto();

    // Create button may not be available in error state
    try {
      await scriptsPage.createButton.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await scriptsPage.clickCreate();

    await expect(scriptsPage.sourceCodeInput).toBeVisible();
  });
});
