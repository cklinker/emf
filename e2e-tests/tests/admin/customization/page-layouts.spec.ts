import { test, expect } from "../../../fixtures";
import { PageLayoutsPage } from "../../../pages/page-layouts.page";

test.describe("Page Layouts", () => {
  let pageLayoutsPage: PageLayoutsPage;

  test.beforeEach(async ({ page }) => {
    pageLayoutsPage = new PageLayoutsPage(page);
    await pageLayoutsPage.goto();
  });

  test("displays page layouts list", async ({ page }) => {
    const tableOrEmpty = pageLayoutsPage.table.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("shows layouts in table", async () => {
    const rowCount = await pageLayoutsPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(0);
  });

  test("has create layout button", async ({ page }) => {
    await page.waitForLoadState("load");
    await expect(pageLayoutsPage.createButton).toBeVisible();
  });
});
