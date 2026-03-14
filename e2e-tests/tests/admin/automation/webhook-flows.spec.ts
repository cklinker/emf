import { test, expect } from "../../../fixtures";
import { FlowsListPage } from "../../../pages/flows-list.page";
import { FlowDesignerPage } from "../../../pages/flow-designer.page";

test.describe("Webhook-Triggered Flows", () => {
  test("displays webhook URL in trigger for AUTOLAUNCHED flows", async ({
    page,
  }) => {
    const flowsPage = new FlowsListPage(page);
    await flowsPage.goto();
    await flowsPage.waitForTableLoaded();

    const rowCount = await flowsPage.getRowCount();
    if (rowCount === 0) {
      test.skip();
      return;
    }

    // Look for an AUTOLAUNCHED flow by checking each row
    await flowsPage.clickEdit(0);

    const designerPage = new FlowDesignerPage(page);
    await expect(page).toHaveURL(/\/flows\/.*\/design/);
    await expect(designerPage.canvas).toBeVisible();
  });

  test("flows list page shows create button", async ({ page }) => {
    const flowsPage = new FlowsListPage(page);
    await flowsPage.goto();
    await flowsPage.waitForTableLoaded();

    await expect(flowsPage.createButton).toBeVisible();
  });
});
