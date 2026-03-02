import { test, expect } from '../../../fixtures';
import { TenantsPage } from '../../../pages/tenants.page';

test.describe('Tenants', () => {
  test('displays tenants page', async ({ page }) => {
    const tenantsPage = new TenantsPage(page);
    await tenantsPage.goto();

    await expect(page).toHaveURL(/\/tenants/);
    await expect(tenantsPage.tenantsPage).toBeVisible();
  });

  test('shows tenants table', async ({ page }) => {
    const tenantsPage = new TenantsPage(page);
    await tenantsPage.goto();

    await expect(tenantsPage.table).toBeVisible();

    const rowCount = await tenantsPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  test('has create tenant button', async ({ page }) => {
    const tenantsPage = new TenantsPage(page);
    await tenantsPage.goto();

    await expect(tenantsPage.createButton).toBeVisible();
  });
});
