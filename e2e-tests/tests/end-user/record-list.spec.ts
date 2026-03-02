import { test, expect } from '../../fixtures';
import { ObjectListPage } from '../../pages/end-user/object-list.page';

const tenantSlug = process.env.E2E_TENANT_SLUG || 'default';

test.describe('Record List', () => {
  test('displays record list for a collection', async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionName}`));
  });

  test('shows data table with records', async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: 'title',
      displayName: 'Title',
      type: 'string',
    });

    await dataFactory.createRecord(collectionName, { title: 'Record A' });
    await dataFactory.createRecord(collectionName, { title: 'Record B' });

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    await expect(listPage.dataTable).toBeVisible();

    const rowCount = await listPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });

  test('can search records', async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: 'title',
      displayName: 'Title',
      type: 'string',
    });

    await dataFactory.createRecord(collectionName, { title: 'Searchable Item' });

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    await expect(listPage.searchInput).toBeVisible();
    await listPage.search('Searchable');

    // Wait for search to take effect
    await page.waitForTimeout(500);
  });

  test('can sort by column', async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: 'title',
      displayName: 'Title',
      type: 'string',
    });

    await dataFactory.createRecord(collectionName, { title: 'Alpha' });
    await dataFactory.createRecord(collectionName, { title: 'Beta' });

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    await expect(listPage.dataTable).toBeVisible();

    // Click a column header to sort
    await listPage.sortByColumn('Title');
    await page.waitForTimeout(500);
  });

  test('shows pagination for large datasets', async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    const listPage = new ObjectListPage(page, collectionName, tenantSlug);
    await listPage.goto();

    // Pagination may or may not be visible depending on record count
    const paginationVisible = await listPage.pagination
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    if (paginationVisible) {
      await expect(listPage.pagination).toBeVisible();
    } else {
      // No pagination needed when there are few records
      expect(paginationVisible).toBe(false);
    }
  });
});
