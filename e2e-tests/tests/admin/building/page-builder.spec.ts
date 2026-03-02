import { test, expect } from "../../../fixtures";
import { PageBuilderPage } from "../../../pages/page-builder.page";

test.describe("Page Builder", () => {
  let pageBuilderPage: PageBuilderPage;

  test.beforeEach(async ({ page }) => {
    pageBuilderPage = new PageBuilderPage(page);
    await pageBuilderPage.goto();
  });

  test("displays page builder page", async () => {
    await expect(pageBuilderPage.pageBuilderPage).toBeVisible();
  });

  test("shows pages table or empty state", async ({ page }) => {
    const tableOrEmpty = pageBuilderPage.table.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("has create page button", async ({ page }) => {
    await page.waitForLoadState("networkidle");
    await expect(pageBuilderPage.createButton).toBeVisible();
  });
});
