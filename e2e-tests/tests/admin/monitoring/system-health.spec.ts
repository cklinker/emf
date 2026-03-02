import { test, expect } from '../../../fixtures';
import { SystemHealthPage } from '../../../pages/system-health.page';

test.describe('System Health', () => {
  let systemHealthPage: SystemHealthPage;

  test.beforeEach(async ({ page }) => {
    systemHealthPage = new SystemHealthPage(page);
    await systemHealthPage.goto();
  });

  test('displays system health dashboard', async () => {
    await expect(systemHealthPage.dashboardPage).toBeVisible();
  });

  test('shows health status cards', async () => {
    await expect(systemHealthPage.healthCards).toBeVisible();
    const cardCount = await systemHealthPage.getHealthCardCount();
    expect(cardCount).toBeGreaterThanOrEqual(0);
  });

  test('shows metrics cards (request rate, error rate, latency)', async () => {
    await expect(systemHealthPage.metricsCards).toBeVisible();
    await expect(systemHealthPage.metricsRequestRate).toBeVisible();
    await expect(systemHealthPage.metricsErrorRate).toBeVisible();
    await expect(systemHealthPage.metricsLatencyP50).toBeVisible();
    await expect(systemHealthPage.metricsLatencyP99).toBeVisible();
  });

  test('has time range selector', async () => {
    await expect(systemHealthPage.timeRangeSelector).toBeVisible();
  });
});
