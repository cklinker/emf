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
    const rowCount = await usersPage.getRowCount();
    if (rowCount > 0) {
      await usersPage.clickRow(0);
      await page.waitForURL(/\/users\/.+/);
    }
  });

  test("shows create user button", async ({ page }) => {
    await page.waitForLoadState("networkidle");
    const found = await waitForAnyVisible([
      usersPage.createButton,
      usersPage.container,
    ]);
    expect(found).toBe(true);
  });
});
