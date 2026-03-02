import { test, expect } from '../../../fixtures';
import { GovernorLimitsPage } from '../../../pages/governor-limits.page';

test.describe('Governor Limits', () => {
  let governorLimitsPage: GovernorLimitsPage;

  test.beforeEach(async ({ page }) => {
    governorLimitsPage = new GovernorLimitsPage(page);
    await governorLimitsPage.goto();
  });

  test('displays governor limits page', async () => {
    await expect(governorLimitsPage.governorLimitsPage).toBeVisible();
  });

  test('shows metric cards', async () => {
    await expect(governorLimitsPage.metricCards).toBeVisible();
    const cardCount = await governorLimitsPage.getMetricCardCount();
    expect(cardCount).toBeGreaterThanOrEqual(0);
  });

  test('shows limits table', async () => {
    await expect(governorLimitsPage.limitsTable).toBeVisible();
  });

  test('has edit button for admin', async () => {
    await expect(governorLimitsPage.editButton).toBeVisible();
  });
});
