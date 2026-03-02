import { test, expect } from "../../../fixtures";
import { MigrationsPage } from "../../../pages/migrations.page";

test.describe("Migrations", () => {
  let migrationsPage: MigrationsPage;

  test.beforeEach(async ({ page }) => {
    migrationsPage = new MigrationsPage(page);
    await migrationsPage.goto();
  });

  test("displays migrations page", async () => {
    await expect(migrationsPage.migrationsPage).toBeVisible();
  });

  test("shows migration history tab", async ({ page }) => {
    await expect(migrationsPage.historyTab).toBeVisible();
    const tableOrEmpty = migrationsPage.historyTable.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("can switch to plan migration tab", async () => {
    await migrationsPage.clickPlanTab();
    await expect(migrationsPage.planTab).toBeVisible();
  });
});
