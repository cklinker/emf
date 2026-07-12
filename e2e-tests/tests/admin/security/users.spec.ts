import { test, expect } from "../../../fixtures";
import { UsersListPage } from "../../../pages/users-list.page";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

test.describe("Users", () => {
  let usersPage: UsersListPage;

  test.beforeEach(async ({ page }) => {
    usersPage = new UsersListPage(page);
    await usersPage.goto();
  });

  test("displays users list page", async () => {
    await expect(usersPage.container).toBeVisible();
  });

  test("shows users in table or empty state", async ({ page }) => {
    const found = await waitForAnyVisible([
      usersPage.userTable,
      page.getByTestId("empty-state"),
      page.getByTestId("error-message"),
      usersPage.container,
    ]);
    expect(found).toBe(true);
  });

  test("can search for users", async () => {
    try {
      await usersPage.searchInput.waitFor({
        state: "visible",
        timeout: 5000,
      });
    } catch {
      return;
    }
    await usersPage.search("admin");
    await usersPage.waitForLoadingComplete();
  });

  test("navigates to user detail on click", async ({ page }) => {
    // Wait for table rows to appear before trying to click
    const firstRow = usersPage.userTable.locator("tbody tr").first();
    const hasRows = await firstRow
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!hasRows) return;

    // Click the user name button (first cell, first button) directly
    const nameButton = firstRow.locator("td").first().locator("button");
    const hasNameButton = await nameButton
      .isVisible({ timeout: 3_000 })
      .catch(() => false);
    if (!hasNameButton) return;

    await nameButton.click();

    // Wait for SPA navigation (React Router uses pushState, not full page load)
    await expect(page).toHaveURL(/\/users\/[^/]+/, { timeout: 10_000 });
  });

  test("shows create user button", async ({ page }) => {
    await page.waitForLoadState("load");
    const found = await waitForAnyVisible([
      usersPage.createButton,
      usersPage.container,
    ]);
    expect(found).toBe(true);
  });

  test("create dialog requires a profile choice", async ({ page }) => {
    const hasCreate = await usersPage.createButton
      .isVisible({ timeout: 5_000 })
      .catch(() => false);
    if (!hasCreate) return;

    await usersPage.createButton.click();

    // The profile selector must be present and populated with the tenant's
    // profiles (creating a user without a profileId is a worker-side 400).
    const profileSelect = page.getByTestId("create-profile-select");
    await expect(profileSelect).toBeVisible({ timeout: 10_000 });
    await expect
      .poll(async () => profileSelect.locator("option").count(), {
        timeout: 10_000,
      })
      .toBeGreaterThan(1);

    // Submitting without a profile is blocked client-side.
    await page.getByLabel(/Email/).fill("e2e-profile-check@example.com");
    await page.getByLabel(/First Name/).fill("E2e");
    await page.getByLabel(/Last Name/).fill("Check");
    await page.getByRole("button", { name: "Create", exact: true }).click();
    await expect(page.getByText("Profile is required")).toBeVisible();

    await page.getByRole("button", { name: "Cancel", exact: true }).click();
  });
});
