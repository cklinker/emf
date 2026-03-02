import { test, expect } from '../../../fixtures';
import { MenuBuilderPage } from '../../../pages/menu-builder.page';

test.describe('Menu Builder', () => {
  let menuBuilderPage: MenuBuilderPage;

  test.beforeEach(async ({ page }) => {
    menuBuilderPage = new MenuBuilderPage(page);
    await menuBuilderPage.goto();
  });

  test('displays menu builder page', async () => {
    await expect(menuBuilderPage.menuBuilderPage).toBeVisible();
  });

  test('shows menu list', async () => {
    await expect(menuBuilderPage.menuList).toBeVisible();
  });

  test('has add menu item button', async () => {
    await expect(menuBuilderPage.addItemButton).toBeVisible();
  });
});
