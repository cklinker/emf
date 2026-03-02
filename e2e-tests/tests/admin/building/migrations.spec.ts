import { test, expect } from '../../../fixtures';
import { MigrationsPage } from '../../../pages/migrations.page';

test.describe('Migrations', () => {
  let migrationsPage: MigrationsPage;

  test.beforeEach(async ({ page }) => {
    migrationsPage = new MigrationsPage(page);
    await migrationsPage.goto();
  });

  test('displays migrations page', async () => {
    await expect(migrationsPage.migrationsPage).toBeVisible();
  });

  test('shows migration history tab', async () => {
    await expect(migrationsPage.historyTab).toBeVisible();
    await expect(migrationsPage.historyTable).toBeVisible();
  });

  test('can switch to plan migration tab', async () => {
    await migrationsPage.clickPlanTab();
    await expect(migrationsPage.planTab).toBeVisible();
  });
});
