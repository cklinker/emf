import { test, expect } from "../../../fixtures";
import { UsersListPage } from "../../../pages/users-list.page";

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
    const tableOrEmpty = usersPage.userTable.or(
      page.getByTestId("empty-state"),
    );
    await expect(tableOrEmpty).toBeVisible();
  });

  test("can search for users", async () => {
    await expect(usersPage.searchInput).toBeVisible();
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
    await expect(usersPage.createButton).toBeVisible();
  });
});
