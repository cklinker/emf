import { test, expect } from '../../../fixtures';
import { PageBuilderPage } from '../../../pages/page-builder.page';

test.describe('Page Builder', () => {
  let pageBuilderPage: PageBuilderPage;

  test.beforeEach(async ({ page }) => {
    pageBuilderPage = new PageBuilderPage(page);
    await pageBuilderPage.goto();
  });

  test('displays page builder page', async () => {
    await expect(pageBuilderPage.pageBuilderPage).toBeVisible();
  });

  test('shows pages table or empty state', async ({ page }) => {
    const rowCount = await pageBuilderPage.getRowCount();
    if (rowCount > 0) {
      await expect(pageBuilderPage.table).toBeVisible();
    } else {
      await expect(
        page.locator('[data-testid="empty-state"], [data-testid="page-builder-table"]'),
      ).toBeVisible();
    }
  });

  test('has create page button', async () => {
    await expect(pageBuilderPage.createButton).toBeVisible();
  });
});
