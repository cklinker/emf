import { test, expect } from '../../../fixtures';
import { UsersListPage } from '../../../pages/users-list.page';

test.describe('Users', () => {
  let usersPage: UsersListPage;

  test.beforeEach(async ({ page }) => {
    usersPage = new UsersListPage(page);
    await usersPage.goto();
  });

  test('displays users list page', async () => {
    await expect(usersPage.container).toBeVisible();
  });

  test('shows users in table', async ({ page }) => {
    await expect(usersPage.userTable).toBeVisible();
    const rowCount = await usersPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  test('can search for users', async () => {
    await expect(usersPage.searchInput).toBeVisible();
    await usersPage.search('admin');
    await usersPage.waitForLoadingComplete();
  });

  test('navigates to user detail on click', async ({ page }) => {
    const rowCount = await usersPage.getRowCount();
    if (rowCount > 0) {
      await usersPage.clickRow(0);
      await page.waitForURL(/\/users\/.+/);
    }
  });

  test('shows create user button', async () => {
    await expect(usersPage.createButton).toBeVisible();
  });
});
