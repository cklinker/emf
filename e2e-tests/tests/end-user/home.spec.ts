import { test, expect } from "../../fixtures";
import { AppHomePage } from "../../pages/end-user/app-home.page";

test.describe("End-User Home", () => {
  test("displays end-user home page", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(page).toHaveURL(/\/app\/home/);
  });

  test("shows quick create section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.quickActions).toBeVisible({ timeout: 10_000 });
  });

  test("shows recent records section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.recentItems).toBeVisible({ timeout: 10_000 });
  });

  test("shows favorites section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.favorites).toBeVisible({ timeout: 10_000 });
  });
});
