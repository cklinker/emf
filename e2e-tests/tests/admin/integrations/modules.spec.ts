import { test, expect } from "../../../fixtures";
import { ModulesPage } from "../../../pages/modules.page";

test.describe("Modules", () => {
  test("displays modules page", async ({ page }) => {
    const modulesPage = new ModulesPage(page);
    await modulesPage.goto();

    await expect(page).toHaveURL(/\/modules/);
    await expect(modulesPage.modulesPage).toBeVisible();
  });

  test("shows installed modules or empty state", async ({ page }) => {
    const modulesPage = new ModulesPage(page);
    await modulesPage.goto();

    const cardsOrEmpty = modulesPage.moduleCards.or(
      page.getByTestId("empty-state"),
    );
    await expect(cardsOrEmpty).toBeVisible();
  });

  test("has install module button", async ({ page }) => {
    const modulesPage = new ModulesPage(page);
    await modulesPage.goto();
    await page.waitForLoadState("networkidle");

    await expect(modulesPage.installButton).toBeVisible();
  });
});
