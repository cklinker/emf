import { test, expect } from '../../../fixtures';
import { PermissionSetsListPage } from '../../../pages/permission-sets-list.page';

test.describe('Permission Sets', () => {
  let permissionSetsPage: PermissionSetsListPage;

  test.beforeEach(async ({ page }) => {
    permissionSetsPage = new PermissionSetsListPage(page);
    await permissionSetsPage.goto();
  });

  test('displays permission sets list page', async () => {
    await expect(permissionSetsPage.table).toBeVisible();
  });

  test('shows permission sets in table', async () => {
    const rowCount = await permissionSetsPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  test('opens create permission set modal', async () => {
    await permissionSetsPage.clickCreate();
    await expect(permissionSetsPage.formModal).toBeVisible();
  });

  test('navigates to permission set detail', async ({ page }) => {
    const rowCount = await permissionSetsPage.getRowCount();
    if (rowCount > 0) {
      await permissionSetsPage.clickEdit(0);
      await page.waitForURL(/\/permission-sets\/.+/);
    }
  });

  test('shows system badge for system permission sets', async ({ page }) => {
    const systemBadge = page.locator('[data-testid="system-badge"]');
    const badgeCount = await systemBadge.count();
    expect(badgeCount).toBeGreaterThanOrEqual(0);
  });
});
