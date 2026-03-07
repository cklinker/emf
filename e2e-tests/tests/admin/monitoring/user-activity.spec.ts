import { test, expect } from "../../../fixtures";
import { UserActivityPage } from "../../../pages/user-activity.page";

test.describe("User Activity", () => {
  let userActivityPage: UserActivityPage;

  test.beforeEach(async ({ page, tenantSlug }) => {
    userActivityPage = new UserActivityPage(page, tenantSlug);
    await userActivityPage.goto();
  });

  test("displays user activity page with title", async ({ page }) => {
    const heading = page.getByRole("heading", { name: /user activity/i });
    await expect(heading).toBeVisible();
  });

  test("shows user selector", async () => {
    await expect(userActivityPage.userSelect).toBeVisible();
  });

  test("shows prompt to select user", async ({ page }) => {
    await expect(page.getByText(/select a user/i)).toBeVisible();
  });

  test("navigates from setup page", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/setup`);
    await page.waitForLoadState("networkidle");
    await page.getByText("User Activity", { exact: true }).click();
    await page.waitForURL(`**/${tenantSlug}/user-activity`);
    const heading = page.getByRole("heading", { name: /user activity/i });
    await expect(heading).toBeVisible();
  });
});
