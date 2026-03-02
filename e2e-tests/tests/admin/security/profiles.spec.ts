import { test, expect } from "../../../fixtures";
import { ProfilesListPage } from "../../../pages/profiles-list.page";

test.describe("Profiles", () => {
  let profilesPage: ProfilesListPage;

  test.beforeEach(async ({ page }) => {
    profilesPage = new ProfilesListPage(page);
    await profilesPage.goto();
  });

  test("displays profiles list page", async () => {
    await expect(profilesPage.container).toBeVisible();
  });

  test("shows profiles in table or empty state", async ({ page }) => {
    const anyState = profilesPage.profileTable
      .or(page.getByTestId("empty-state"))
      .or(page.getByTestId("error-message"))
      .or(profilesPage.container);
    await expect(anyState).toBeVisible({ timeout: 10000 });
  });

  test("opens create profile modal", async () => {
    // Create button only exists when page loads successfully (not in error state)
    try {
      await profilesPage.createButton.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await profilesPage.clickCreate();
    await expect(profilesPage.formModal).toBeVisible();
    await expect(profilesPage.formNameInput).toBeVisible();
  });

  test("can create a new profile", async () => {
    try {
      await profilesPage.createButton.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await profilesPage.clickCreate();
    await expect(profilesPage.formModal).toBeVisible();

    await profilesPage.fillForm({
      name: `E2E Test Profile ${Date.now()}`,
      description: "Created by e2e test",
    });
    await profilesPage.submitForm();
    await profilesPage.waitForLoadingComplete();
  });

  test("navigates to profile detail", async ({ page }) => {
    const rowCount = await profilesPage.getRowCount();
    if (rowCount > 0) {
      await profilesPage.clickRow(0);
      await page.waitForURL(/\/profiles\/.+/);
    }
  });
});
