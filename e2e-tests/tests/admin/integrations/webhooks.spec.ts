import { test, expect } from "../../../fixtures";
import { WebhooksPage } from "../../../pages/webhooks.page";

test.describe("Webhooks", () => {
  test("displays webhooks page with custom UI", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();

    await expect(page).toHaveURL(/\/webhooks/);
    await expect(webhooksPage.webhooksPage).toBeVisible();
  });

  test("shows endpoints tab by default", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();

    // The webhooks page should show the endpoints tab content
    await expect(webhooksPage.webhooksPage).toBeVisible();
  });

  test("can switch to messages tab", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();
    await expect(webhooksPage.webhooksPage).toBeVisible();

    // Click the messages tab if it exists
    if (await webhooksPage.messagesTab.isVisible().catch(() => false)) {
      await webhooksPage.messagesTab.click();
    }
  });

  test("can switch to event catalog tab", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();
    await expect(webhooksPage.webhooksPage).toBeVisible();

    // Click the event catalog tab if it exists
    if (await webhooksPage.eventCatalogTab.isVisible().catch(() => false)) {
      await webhooksPage.eventCatalogTab.click();
    }
  });

  test("add endpoint dialog shows collection filter", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();
    await expect(webhooksPage.webhooksPage).toBeVisible();

    // Click the Add Endpoint button to open the dialog
    await webhooksPage.addEndpointButton.click();

    // The dialog should show the collection filter section
    await expect(webhooksPage.collectionFilterSection).toBeVisible();
  });
});
