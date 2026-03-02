import { test, expect } from '../../../fixtures';
import { ProfilesListPage } from '../../../pages/profiles-list.page';

test.describe('Profiles', () => {
  let profilesPage: ProfilesListPage;

  test.beforeEach(async ({ page }) => {
    profilesPage = new ProfilesListPage(page);
    await profilesPage.goto();
  });

  test('displays profiles list page', async () => {
    await expect(profilesPage.container).toBeVisible();
  });

  test('shows profiles in table', async () => {
    await expect(profilesPage.profileTable).toBeVisible();
    const rowCount = await profilesPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  test('opens create profile modal', async () => {
    await profilesPage.clickCreate();
    await expect(profilesPage.formModal).toBeVisible();
    await expect(profilesPage.formNameInput).toBeVisible();
  });

  test('can create a new profile', async () => {
    await profilesPage.clickCreate();
    await expect(profilesPage.formModal).toBeVisible();

    await profilesPage.fillForm({
      name: `E2E Test Profile ${Date.now()}`,
      description: 'Created by e2e test',
    });
    await profilesPage.submitForm();
    await profilesPage.waitForLoadingComplete();
  });

  test('navigates to profile detail', async ({ page }) => {
    const rowCount = await profilesPage.getRowCount();
    if (rowCount > 0) {
      await profilesPage.clickRow(0);
      await page.waitForURL(/\/profiles\/.+/);
    }
  });
});
