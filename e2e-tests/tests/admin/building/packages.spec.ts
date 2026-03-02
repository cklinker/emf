import { test, expect } from "../../../fixtures";
import { PackagesPage } from "../../../pages/packages.page";

test.describe("Packages", () => {
  let packagesPage: PackagesPage;

  test.beforeEach(async ({ page }) => {
    packagesPage = new PackagesPage(page);
    await packagesPage.goto();
  });

  test("displays packages page", async () => {
    await expect(packagesPage.packagesPage).toBeVisible();
  });

  test("shows export tab by default", async () => {
    await expect(packagesPage.tabExport).toBeVisible();
  });

  test("can switch to import tab", async () => {
    await packagesPage.clickImportTab();
    await expect(packagesPage.tabImport).toBeVisible();
  });

  test("can switch to history tab", async () => {
    await packagesPage.clickHistoryTab();
    await expect(packagesPage.tabHistory).toBeVisible();
  });
});
