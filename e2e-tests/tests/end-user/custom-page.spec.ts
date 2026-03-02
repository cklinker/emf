import { test, expect } from '../../fixtures';
import { CustomPagePage } from '../../pages/end-user/custom-page.page';

test.describe('Custom Page', () => {
  test('displays custom page content', async ({ page }) => {
    const customPage = new CustomPagePage(page, 'home');
    await customPage.goto();

    await expect(page).toHaveURL(/\/app\/p\/home/);

    const isVisible = await customPage.isContentVisible();
    expect(isVisible).toBe(true);
  });

  test('shows page based on slug', async ({ page }) => {
    const slug = 'home';
    const customPage = new CustomPagePage(page, slug);
    await customPage.goto();

    await expect(page).toHaveURL(new RegExp(`/app/p/${slug}`));
    await expect(customPage.pageContent).toBeVisible();
  });
});
