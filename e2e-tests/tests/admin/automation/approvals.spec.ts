import { test, expect } from "../../../fixtures";
import { ApprovalsPage } from "../../../pages/approvals.page";

test.describe("Approval Processes", () => {
  test("displays approval processes page", async ({ page }) => {
    const approvalsPage = new ApprovalsPage(page);
    await approvalsPage.goto();

    await expect(page).toHaveURL(/\/approvals/);
  });

  test("shows approvals table or empty state", async ({ page }) => {
    const approvalsPage = new ApprovalsPage(page);
    await approvalsPage.goto();

    const tableOrEmpty = approvalsPage.table.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("opens create approval process form", async ({ page }) => {
    const approvalsPage = new ApprovalsPage(page);
    await approvalsPage.goto();

    await approvalsPage.clickCreate();

    await expect(approvalsPage.formModal).toBeVisible();
    await expect(approvalsPage.nameInput).toBeVisible();
  });

  test("closes form on cancel", async ({ page }) => {
    const approvalsPage = new ApprovalsPage(page);
    await approvalsPage.goto();

    await approvalsPage.clickCreate();
    await expect(approvalsPage.formModal).toBeVisible();

    await approvalsPage.cancelButton.click();
    await expect(approvalsPage.formModal).not.toBeVisible();
  });
});
