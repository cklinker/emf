import { test, expect } from "../../../fixtures";
import { WebhooksPage } from "../../../pages/webhooks.page";

test.describe("Webhooks", () => {
  test("displays webhooks page", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();

    await expect(page).toHaveURL(/\/webhooks/);
    await expect(webhooksPage.webhooksPage).toBeVisible();
  });

  test("shows webhooks table or empty state", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();

    const tableOrEmpty = webhooksPage.table.or(page.getByTestId("empty-state"));
    await expect(tableOrEmpty).toBeVisible();
  });

  test("opens create webhook form", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();

    await webhooksPage.clickCreate();

    await expect(webhooksPage.formModal).toBeVisible();
    await expect(webhooksPage.nameInput).toBeVisible();
  });

  test("can fill URL and events in form", async ({ page }) => {
    const webhooksPage = new WebhooksPage(page);
    await webhooksPage.goto();

    await webhooksPage.clickCreate();
    await webhooksPage.fillForm({
      name: "Test Webhook",
      url: "https://example.com/webhook",
      events: "record.created,record.updated",
    });

    await expect(webhooksPage.nameInput).toHaveValue("Test Webhook");
    await expect(webhooksPage.urlInput).toHaveValue(
      "https://example.com/webhook",
    );
    await expect(webhooksPage.eventsInput).toHaveValue(
      "record.created,record.updated",
    );
  });
});
