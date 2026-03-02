import { test, expect } from '../../fixtures';
import { LoginPage } from '../../pages/login.page';

const tenantSlug = process.env.E2E_TENANT_SLUG || 'default';

test.use({ storageState: { cookies: [], origins: [] } });

test.describe('Login Page', () => {
  test('shows login page with OIDC provider buttons', async ({ page }) => {
    const loginPage = new LoginPage(page, tenantSlug);
    await loginPage.goto();

    await expect(loginPage.container).toBeVisible();

    const providerNames = await loginPage.getProviderNames();
    expect(providerNames.length).toBeGreaterThan(0);
  });

  test('redirects unauthenticated users to login page', async ({ page }) => {
    await page.goto(`/${tenantSlug}/collections`);

    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/login`));
  });

  test('displays error message on failed login', async ({ page }) => {
    const loginPage = new LoginPage(page, tenantSlug);
    await page.goto(`/${tenantSlug}/login?error=auth_failed`);

    await expect(loginPage.errorMessage).toBeVisible();
  });

  test('auto-redirects when single provider exists', async ({ page }) => {
    const loginPage = new LoginPage(page, tenantSlug);
    await loginPage.goto();

    // When only one OIDC provider is configured, the login page should
    // auto-redirect to the provider's auth endpoint instead of showing buttons.
    const providerNames = await loginPage.getProviderNames();

    if (providerNames.length === 1) {
      // Should have navigated away from the login page automatically
      await expect(page).not.toHaveURL(new RegExp(`/${tenantSlug}/login$`));
    } else {
      // Multiple providers: buttons should be visible for manual selection
      await expect(loginPage.providerButtons.first()).toBeVisible();
    }
  });
});
