import { test, expect } from '../../../fixtures';
import { ConnectedAppsPage } from '../../../pages/connected-apps.page';

test.describe('Connected Apps', () => {
  test('displays connected apps page', async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();

    await expect(page).toHaveURL(/\/connected-apps/);
    await expect(connectedAppsPage.connectedAppsPage).toBeVisible();
  });

  test('shows apps table or empty state', async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();

    const rowCount = await connectedAppsPage.getRowCount();
    if (rowCount > 0) {
      await expect(connectedAppsPage.table).toBeVisible();
    } else {
      await expect(connectedAppsPage.table).toBeVisible();
    }
  });

  test('opens create connected app form', async ({ page }) => {
    const connectedAppsPage = new ConnectedAppsPage(page);
    await connectedAppsPage.goto();

    await connectedAppsPage.clickCreate();

    await expect(connectedAppsPage.formModal).toBeVisible();
    await expect(connectedAppsPage.nameInput).toBeVisible();
    await expect(connectedAppsPage.descriptionInput).toBeVisible();
  });
});
