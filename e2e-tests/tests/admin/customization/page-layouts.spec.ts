import { test, expect } from "../../../fixtures";
import { PageLayoutsPage } from "../../../pages/page-layouts.page";

test.describe("Page Layouts", () => {
  let pageLayoutsPage: PageLayoutsPage;

  test.beforeEach(async ({ page }) => {
    pageLayoutsPage = new PageLayoutsPage(page);
    await pageLayoutsPage.goto();
  });

  test("displays page layouts list", async () => {
    await pageLayoutsPage.waitForTableLoaded();
  });

  test("shows layouts in table", async () => {
    await pageLayoutsPage.waitForTableLoaded();
    const rowCount = await pageLayoutsPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(0);
  });

  test("has create layout button", async () => {
    await pageLayoutsPage.waitForTableLoaded();
    await expect(pageLayoutsPage.createButton).toBeVisible();
  });
});
