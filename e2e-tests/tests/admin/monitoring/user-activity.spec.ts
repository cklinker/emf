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
    await expect(
      page.getByText(/select a user to view their activity/i),
    ).toBeVisible();
  });

  test("navigates via monitoring hub tabs", async ({ page, tenantSlug }) => {
    await page.goto(`/${tenantSlug}/monitoring`);
    await page.waitForLoadState("load");
    await page.getByTestId("monitoring-tab-activity").click();
    await page.waitForURL(`**/${tenantSlug}/monitoring/activity`);
    const heading = page.getByRole("heading", { name: /user activity/i });
    await expect(heading).toBeVisible();
  });
});
