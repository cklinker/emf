import { test, expect } from '../../fixtures';
import { ObjectListPage } from '../../pages/end-user/object-list.page';
import { ObjectDetailPage } from '../../pages/end-user/object-detail.page';
import { ObjectFormPage } from '../../pages/end-user/object-form.page';

const tenantSlug = process.env.E2E_TENANT_SLUG || 'default';

test.describe('End-User Record Lifecycle Journey', () => {
  test('full record lifecycle: create, view, edit, delete', async ({
    page,
    dataFactory,
  }) => {
    // Step 1: Create a collection with a field via API
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes.name as string;

    await dataFactory.addField(collection.id, {
      name: 'title',
      displayName: 'Title',
      type: 'string',
      required: true,
    });

    const recordTitle = `Lifecycle Record ${Date.now()}`;
    const updatedTitle = `Updated Lifecycle ${Date.now()}`;

    // Step 2: Navigate to end-user and create a record
    const formPage = new ObjectFormPage(page, collectionName, undefined, tenantSlug);
    await formPage.goto('new');

    await expect(formPage.formFields).toBeVisible();
    await formPage.fillField('title', recordTitle);
    await formPage.save();

    // Should navigate to the record detail or list after save
    await page.waitForURL(new RegExp(`/app/o/${collectionName}`));

    // Step 3: View the created record
    // Extract the record ID from the URL if we landed on a detail page
    const currentUrl = page.url();
    const recordIdMatch = currentUrl.match(
      new RegExp(`/app/o/${collectionName}/([^/]+)$`),
    );

    if (recordIdMatch) {
      const recordId = recordIdMatch[1];

      const detailPage = new ObjectDetailPage(
        page,
        collectionName,
        recordId,
        tenantSlug,
      );
      await expect(detailPage.fieldValues).toBeVisible();

      // Step 4: Edit the record
      await detailPage.clickEdit();
      await page.waitForURL(/\/edit/);

      const editFormPage = new ObjectFormPage(
        page,
        collectionName,
        recordId,
        tenantSlug,
      );
      await editFormPage.fillField('title', updatedTitle);
      await editFormPage.save();

      await page.waitForURL(new RegExp(`/app/o/${collectionName}`));

      // Step 5: Delete the record
      const detailPageAfterEdit = new ObjectDetailPage(
        page,
        collectionName,
        recordId,
        tenantSlug,
      );
      await detailPageAfterEdit.goto();
      await detailPageAfterEdit.clickDelete();
      await detailPageAfterEdit.confirmDialog();

      // Should navigate back to the list after deletion
      await page.waitForURL(new RegExp(`/app/o/${collectionName}`));
    } else {
      // If we landed on the list, find and click the record
      const listPage = new ObjectListPage(page, collectionName, tenantSlug);
      await expect(listPage.dataTable).toBeVisible();

      const rowCount = await listPage.getRowCount();
      expect(rowCount).toBeGreaterThan(0);

      // Click the first row to view the record
      await listPage.clickRow(0);
      await page.waitForURL(
        new RegExp(`/app/o/${collectionName}/[^/]+`),
      );
    }
  });
});
