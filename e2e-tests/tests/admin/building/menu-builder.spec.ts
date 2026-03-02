import { test, expect } from "../../../fixtures";
import { MenuBuilderPage } from "../../../pages/menu-builder.page";

test.describe("Menu Builder", () => {
  let menuBuilderPage: MenuBuilderPage;

  test.beforeEach(async ({ page }) => {
    menuBuilderPage = new MenuBuilderPage(page);
    await menuBuilderPage.goto();
  });

  test("displays menu builder page", async () => {
    await expect(menuBuilderPage.menuBuilderPage).toBeVisible();
  });

  test("shows menu list or empty state", async ({ page }) => {
    const listOrEmpty = menuBuilderPage.menuList.or(
      page.getByTestId("empty-state"),
    );
    await expect(listOrEmpty).toBeVisible();
  });

  test("has add menu item button", async ({ page }) => {
    await page.waitForLoadState("networkidle");
    await expect(menuBuilderPage.addItemButton).toBeVisible();
  });
});
