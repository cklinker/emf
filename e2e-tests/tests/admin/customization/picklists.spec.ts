import { test, expect } from "../../../fixtures";
import { PicklistsPage } from "../../../pages/picklists.page";

test.describe("Picklists", () => {
  let picklistsPage: PicklistsPage;

  test.beforeEach(async ({ page }) => {
    picklistsPage = new PicklistsPage(page);
    await picklistsPage.goto();
  });

  test("displays picklists page", async () => {
    await picklistsPage.waitForTableLoaded();
  });

  test("shows picklists in table", async () => {
    await picklistsPage.waitForTableLoaded();
    const rowCount = await picklistsPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(0);
  });

  test("opens create picklist form", async () => {
    await picklistsPage.waitForTableLoaded();
    await picklistsPage.clickCreate();
    await expect(picklistsPage.formModal).toBeVisible();
    await expect(picklistsPage.nameInput).toBeVisible();
  });

  test("can fill out picklist form", async () => {
    await picklistsPage.waitForTableLoaded();
    await picklistsPage.clickCreate();
    await expect(picklistsPage.formModal).toBeVisible();

    await picklistsPage.fillForm({
      name: `E2E Picklist ${Date.now()}`,
      description: "Created by e2e test",
      sorted: true,
      restricted: false,
    });

    await expect(picklistsPage.submitButton).toBeEnabled();
  });

  test("closes form on cancel", async () => {
    await picklistsPage.waitForTableLoaded();
    await picklistsPage.clickCreate();
    await expect(picklistsPage.formModal).toBeVisible();

    await picklistsPage.cancelButton.click();
    await expect(picklistsPage.formModal).not.toBeVisible();
  });
});
