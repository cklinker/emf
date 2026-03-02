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

    const tableOrEmpty = scriptsPage.table.or(page.getByTestId("empty-state"));
    await expect(tableOrEmpty).toBeVisible();
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
