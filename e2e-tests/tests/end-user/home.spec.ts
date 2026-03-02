import { test, expect } from '../../fixtures';
import { AppHomePage } from '../../pages/end-user/app-home.page';

test.describe('End-User Home', () => {
  test('displays end-user home page', async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(page).toHaveURL(/\/app\/home/);
  });

  test('shows quick actions section', async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.quickActions).toBeVisible();

    const actionCount = await homePage.getQuickActionCount();
    expect(actionCount).toBeGreaterThanOrEqual(0);
  });

  test('shows recent items section', async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.recentItems).toBeVisible();
  });

  test('shows favorites section', async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.favorites).toBeVisible();
  });
});
