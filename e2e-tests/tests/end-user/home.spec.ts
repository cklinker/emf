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

    await expect(
      homePage.quickActions.or(
        page.getByRole("heading", { name: /quick actions/i }),
      ),
    ).toBeVisible({ timeout: 10_000 });
  });

  test("shows recent records section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(
      homePage.recentItems.or(
        page.getByRole("heading", { name: /recent items/i }),
      ),
    ).toBeVisible({ timeout: 10_000 });
  });

  test("shows favorites section", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(
      homePage.favorites.or(page.getByRole("heading", { name: /favorites/i })),
    ).toBeVisible({ timeout: 10_000 });
  });
});
