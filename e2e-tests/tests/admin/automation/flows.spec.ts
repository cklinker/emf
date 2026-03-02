import { test, expect } from "../../../fixtures";
import { FlowsListPage } from "../../../pages/flows-list.page";
import { FlowDesignerPage } from "../../../pages/flow-designer.page";

test.describe("Flows", () => {
  test("displays flows list page", async ({ page }) => {
    const flowsPage = new FlowsListPage(page);
    await flowsPage.goto();

    await expect(page).toHaveURL(/\/flows/);
  });

  test("shows flows table or empty state", async ({ page }) => {
    const flowsPage = new FlowsListPage(page);
    await flowsPage.goto();

    const tableOrEmpty = flowsPage.table.or(page.getByTestId("empty-state"));
    await expect(tableOrEmpty).toBeVisible();
  });

  test("has create flow button", async ({ page }) => {
    const flowsPage = new FlowsListPage(page);
    await flowsPage.goto();
    await page.waitForLoadState("networkidle");

    await expect(flowsPage.createButton).toBeVisible();
  });

  test("opens flow designer from list", async ({ page }) => {
    const flowsPage = new FlowsListPage(page);
    await flowsPage.goto();

    const rowCount = await flowsPage.getRowCount();
    if (rowCount > 0) {
      await flowsPage.clickEdit(0);

      const designerPage = new FlowDesignerPage(page);
      await expect(page).toHaveURL(/\/flows\/.*\/design/);
      await expect(designerPage.canvas).toBeVisible();
    }
  });
});
