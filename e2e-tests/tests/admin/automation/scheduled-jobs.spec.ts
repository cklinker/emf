import { test, expect } from '../../../fixtures';
import { ScheduledJobsPage } from '../../../pages/scheduled-jobs.page';

test.describe('Scheduled Jobs', () => {
  test('displays scheduled jobs page', async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await expect(page).toHaveURL(/\/scheduled-jobs/);
  });

  test('shows scheduled jobs table', async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await expect(scheduledJobsPage.table).toBeVisible();
  });

  test('opens create scheduled job form', async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await scheduledJobsPage.clickCreate();

    await expect(scheduledJobsPage.formModal).toBeVisible();
    await expect(scheduledJobsPage.nameInput).toBeVisible();
  });

  test('can fill out job form fields', async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await scheduledJobsPage.clickCreate();
    await scheduledJobsPage.fillForm({
      name: 'Test Scheduled Job',
      type: 'data-sync',
      cronExpression: '0 0 * * *',
      active: true,
    });

    await expect(scheduledJobsPage.nameInput).toHaveValue('Test Scheduled Job');
    await expect(scheduledJobsPage.cronInput).toHaveValue('0 0 * * *');
  });

  test('closes form on cancel', async ({ page }) => {
    const scheduledJobsPage = new ScheduledJobsPage(page);
    await scheduledJobsPage.goto();

    await scheduledJobsPage.clickCreate();
    await expect(scheduledJobsPage.formModal).toBeVisible();

    await scheduledJobsPage.cancelButton.click();
    await expect(scheduledJobsPage.formModal).not.toBeVisible();
  });
});
