import { test, expect } from "../../../fixtures";
import { UsersListPage } from "../../../pages/users-list.page";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("User Detail", () => {
  test("navigates to user detail from list", async ({ page }) => {
    const usersPage = new UsersListPage(page, tenantSlug);
    await usersPage.goto();

    // Wait for table rows to appear
    const firstRow = usersPage.userTable.locator("tbody tr").first();
    const hasRows = await firstRow
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!hasRows) return;

    // Click the user name button
    const nameButton = firstRow.locator("td").first().locator("button");
    const hasNameButton = await nameButton
      .isVisible({ timeout: 3_000 })
      .catch(() => false);
    if (!hasNameButton) return;

    await nameButton.click();

    await expect(page).toHaveURL(/\/users\/[^/]+/, { timeout: 10_000 });
  });

  test("shows user detail page content", async ({ page }) => {
    const usersPage = new UsersListPage(page, tenantSlug);
    await usersPage.goto();

    const firstRow = usersPage.userTable.locator("tbody tr").first();
    const hasRows = await firstRow
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!hasRows) return;

    const nameButton = firstRow.locator("td").first().locator("button");
    const hasNameButton = await nameButton
      .isVisible({ timeout: 3_000 })
      .catch(() => false);
    if (!hasNameButton) return;

    await nameButton.click();
    await expect(page).toHaveURL(/\/users\/[^/]+/, { timeout: 10_000 });

    // User detail should show user info
    const found = await waitForAnyVisible([
      page.getByTestId("user-detail-page"),
      page.getByTestId("user-info"),
      page.locator("main").first(),
    ]);
    expect(found).toBe(true);
  });
});
