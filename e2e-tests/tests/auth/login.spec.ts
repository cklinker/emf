import { test, expect } from "../../fixtures";
import { LoginPage } from "../../pages/login.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

// These tests run unauthenticated to verify login page behavior.
test.use({ storageState: { cookies: [], origins: [] } });

test.describe("Login Page", () => {
  test("displays error message on failed login", async ({ page }) => {
    const loginPage = new LoginPage(page, tenantSlug);
    await page.goto(`/${tenantSlug}/login?error=auth_failed`);

    await expect(loginPage.errorMessage).toBeVisible();
  });

  test("shows provider buttons on login page", async ({ page }) => {
    await page.goto(`/${tenantSlug}/login`);

    const loginPage = new LoginPage(page, tenantSlug);
    await expect(loginPage.container).toBeVisible();
    await expect(loginPage.providerButtons.first()).toBeVisible();
  });
});
