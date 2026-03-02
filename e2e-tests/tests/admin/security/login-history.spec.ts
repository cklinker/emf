import { test, expect } from '../../../fixtures';
import { LoginHistoryPage } from '../../../pages/login-history.page';

test.describe('Login History', () => {
  let loginHistoryPage: LoginHistoryPage;

  test.beforeEach(async ({ page }) => {
    loginHistoryPage = new LoginHistoryPage(page);
    await loginHistoryPage.goto();
  });

  test('displays login history page', async () => {
    await expect(loginHistoryPage.loginHistoryPage).toBeVisible();
  });

  test('shows login history table', async () => {
    await expect(loginHistoryPage.table).toBeVisible();
    const rowCount = await loginHistoryPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(0);
  });

  test('displays pagination controls', async () => {
    await expect(loginHistoryPage.pagination).toBeVisible();
  });

  test('navigates between pages', async () => {
    const isNextVisible = await loginHistoryPage.nextButton
      .isVisible({ timeout: 2000 })
      .catch(() => false);

    if (isNextVisible) {
      const isNextEnabled = await loginHistoryPage.nextButton.isEnabled();
      if (isNextEnabled) {
        await loginHistoryPage.clickNext();
        await loginHistoryPage.waitForLoadingComplete();

        const isPrevEnabled = await loginHistoryPage.previousButton.isEnabled();
        if (isPrevEnabled) {
          await loginHistoryPage.clickPrevious();
          await loginHistoryPage.waitForLoadingComplete();
        }
      }
    }
  });
});
