import { test, expect } from "../../fixtures";
import { AppHomePage } from "../../pages/end-user/app-home.page";

test.describe("End-User Navigation", () => {
  test("shows top navigation bar", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    await expect(homePage.header).toBeVisible();
  });

  test("shows user menu or avatar button", async ({ page }) => {
    const homePage = new AppHomePage(page);
    await homePage.goto();

    // The user menu may be a button with the user's name/initials
    const userMenu = page.getByTestId("user-menu");
    const avatarButton = page.getByRole("button", { name: /e2e|user|avatar/i });
    const userMenuOrAvatar = userMenu.or(avatarButton);
    await expect(userMenuOrAvatar).toBeVisible();
  });

  // Skip: test body is incomplete — no assertions after creating collection
  test.skip("navigates between collections", async ({ page, dataFactory }) => {
    await dataFactory.createCollection({ displayName: "Nav Test A" });
  });

  // Skip: test body is incomplete — no assertions after creating collection
  test.skip("shows collection tabs in nav", async ({ page, dataFactory }) => {
    await dataFactory.createCollection();
  });
});
