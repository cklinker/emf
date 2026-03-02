import { test, expect } from '../../fixtures';
import { CollectionsListPage } from '../../pages/collections-list.page';
import { ObjectListPage } from '../../pages/end-user/object-list.page';

const tenantSlug = process.env.E2E_TENANT_SLUG || 'default';

test.describe('Admin Collection Setup Journey', () => {
  test('creates a collection in admin and verifies it in end-user app', async ({
    page,
    dataFactory,
  }) => {
    // Step 1: Create a collection via API
    const collection = await dataFactory.createCollection({
      displayName: `Journey Test ${Date.now()}`,
    });
    const collectionName = collection.attributes.name as string;
    const displayName = collection.attributes.displayName as string;

    // Step 2: Add fields to the collection
    await dataFactory.addField(collection.id, {
      name: 'title',
      displayName: 'Title',
      type: 'string',
      required: true,
    });

    await dataFactory.addField(collection.id, {
      name: 'description',
      displayName: 'Description',
      type: 'string',
    });

    // Step 3: Navigate to admin collections page and verify in list
    const collectionsPage = new CollectionsListPage(page, tenantSlug);
    await collectionsPage.goto();

    await expect(collectionsPage.container).toBeVisible();

    // Filter to find the newly created collection
    await collectionsPage.filterByName(displayName);
    await page.waitForTimeout(500);

    const names = await collectionsPage.getCollectionNames();
    expect(names.length).toBeGreaterThan(0);

    const found = names.some((name) =>
      name.toLowerCase().includes(displayName.toLowerCase()),
    );
    expect(found).toBe(true);

    // Step 4: Switch to end-user object list and verify the collection is accessible
    const objectListPage = new ObjectListPage(page, collectionName, tenantSlug);
    await objectListPage.goto();

    await expect(page).toHaveURL(new RegExp(`/app/o/${collectionName}`));
  });
});
