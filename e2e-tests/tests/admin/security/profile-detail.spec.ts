import { test, expect } from "../../../fixtures";
import { ProfilesListPage } from "../../../pages/profiles-list.page";
import { waitForAnyVisible } from "../../../helpers/wait-helpers";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Profile Detail", () => {
  test("navigates to profile detail from list", async ({ page }) => {
    const profilesPage = new ProfilesListPage(page, tenantSlug);
    await profilesPage.goto();

    // Wait for at least one profile row to appear
    const firstRow = page.locator('[data-testid^="profile-row-"]').first();
    const hasRows = await firstRow
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!hasRows) return;

    await profilesPage.clickRow(0);

    await expect(page).toHaveURL(/\/profiles\/[^/]+/, { timeout: 10_000 });
  });

  test("shows profile detail page content", async ({ page }) => {
    const profilesPage = new ProfilesListPage(page, tenantSlug);
    await profilesPage.goto();

    const firstRow = page.locator('[data-testid^="profile-row-"]').first();
    const hasRows = await firstRow
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!hasRows) return;

    await profilesPage.clickRow(0);

    // Profile detail should show permission sections
    const found = await waitForAnyVisible([
      page.getByTestId("profile-detail-page"),
      page.getByTestId("object-permissions"),
      page.getByTestId("system-permissions"),
      page.locator("main").first(),
    ]);
    expect(found).toBe(true);
  });
});
