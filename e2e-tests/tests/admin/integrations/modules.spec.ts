import { test, expect } from '../../../fixtures';
import { ModulesPage } from '../../../pages/modules.page';

test.describe('Modules', () => {
  test('displays modules page', async ({ page }) => {
    const modulesPage = new ModulesPage(page);
    await modulesPage.goto();

    await expect(page).toHaveURL(/\/modules/);
    await expect(modulesPage.modulesPage).toBeVisible();
  });

  test('shows installed modules or empty state', async ({ page }) => {
    const modulesPage = new ModulesPage(page);
    await modulesPage.goto();

    const moduleCount = await modulesPage.getModuleCount();
    if (moduleCount > 0) {
      await expect(modulesPage.moduleCards).toBeVisible();
    } else {
      await expect(modulesPage.emptyState).toBeVisible();
    }
  });

  test('has install module button', async ({ page }) => {
    const modulesPage = new ModulesPage(page);
    await modulesPage.goto();

    await expect(modulesPage.installButton).toBeVisible();
  });
});
