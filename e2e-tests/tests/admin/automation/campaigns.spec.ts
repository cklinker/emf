import { test, expect } from "../../../fixtures";
import { CampaignsPage } from "../../../pages/campaigns.page";

test.describe("Campaigns", () => {
  test("displays campaigns page", async ({ page }) => {
    const campaignsPage = new CampaignsPage(page);
    await campaignsPage.goto();

    await expect(page).toHaveURL(/\/campaigns/);
  });

  test("shows campaigns table or empty state", async ({ page }) => {
    const campaignsPage = new CampaignsPage(page);
    await campaignsPage.goto();
    await campaignsPage.waitForTableLoaded();
  });

  test("opens the new campaign form", async ({ page }) => {
    const campaignsPage = new CampaignsPage(page);
    await campaignsPage.goto();
    await campaignsPage.waitForTableLoaded();

    await campaignsPage.clickCreate();

    await expect(campaignsPage.formModal).toBeVisible();
    await expect(campaignsPage.nameInput).toBeVisible();
    await expect(campaignsPage.subjectInput).toBeVisible();
    await expect(campaignsPage.targetCollectionSelect).toBeVisible();
    await expect(campaignsPage.recipientFieldInput).toBeVisible();
  });

  test("creates a campaign and shows it in the list", async ({ page }) => {
    const campaignsPage = new CampaignsPage(page);
    await campaignsPage.goto();
    await campaignsPage.waitForTableLoaded();

    await campaignsPage.clickCreate();

    const name = `Newsletter ${Date.now()}`;
    await campaignsPage.nameInput.fill(name);
    await campaignsPage.subjectInput.fill("Monthly newsletter");
    await campaignsPage.recipientFieldInput.fill("email");

    // Pick the first real collection option (index 0 is the placeholder).
    const optionValues = await campaignsPage.targetCollectionSelect
      .locator("option")
      .evaluateAll((opts) =>
        (opts as HTMLOptionElement[])
          .map((o) => o.value)
          .filter((v) => v.length > 0),
      );
    expect(optionValues.length).toBeGreaterThan(0);
    await campaignsPage.targetCollectionSelect.selectOption(optionValues[0]);

    await campaignsPage.submitForm();

    await expect(campaignsPage.formModal).toBeHidden();
    await expect(campaignsPage.table).toContainText(name);
  });
});
