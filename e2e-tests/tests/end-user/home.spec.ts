import { test, expect } from "../../fixtures";
import { AppHomePage } from "../../pages/end-user/app-home.page";

test.describe("End-User Home", () => {
  test("displays end-user home page", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(page).toHaveURL(/\/app\/home/);
  });

  test("shows quick actions section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    const actionsOrEmpty = homePage.quickActions.or(
      page.getByTestId("empty-state"),
    );
    await expect(actionsOrEmpty).toBeVisible();
  });

  test("shows recent items section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    const recentOrEmpty = homePage.recentItems.or(
      page.getByTestId("empty-state"),
    );
    await expect(recentOrEmpty).toBeVisible();
  });

  test("shows favorites section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    const favoritesOrEmpty = homePage.favorites.or(
      page.getByTestId("empty-state"),
    );
    await expect(favoritesOrEmpty).toBeVisible();
  });
});
