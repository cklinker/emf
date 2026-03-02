import { test, expect } from '../../../fixtures';
import { ResourceBrowserPage } from '../../../pages/resource-browser.page';

test.describe('Resource Browser', () => {
  let resourceBrowserPage: ResourceBrowserPage;

  test.beforeEach(async ({ page }) => {
    resourceBrowserPage = new ResourceBrowserPage(page);
    await resourceBrowserPage.goto();
  });

  test('displays resource browser page', async () => {
    await expect(resourceBrowserPage.resourceBrowserPage).toBeVisible();
  });

  test('shows collection cards grid', async () => {
    await expect(resourceBrowserPage.collectionsGrid).toBeVisible();
  });

  test('can search for collections', async () => {
    await expect(resourceBrowserPage.collectionSearchInput).toBeVisible();
    await resourceBrowserPage.search('test');
    await expect(resourceBrowserPage.collectionSearchInput).toHaveValue('test');
  });

  test('can clear search', async () => {
    await resourceBrowserPage.search('test');
    await resourceBrowserPage.clearSearch();
    await expect(resourceBrowserPage.collectionSearchInput).toHaveValue('');
  });

  test('shows results count', async () => {
    await expect(resourceBrowserPage.resultsCountLabel).toBeVisible();
  });
});
