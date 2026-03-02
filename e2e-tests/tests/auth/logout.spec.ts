import { test, expect } from '../../fixtures';
import { LoginPage } from '../../pages/login.page';

const tenantSlug = process.env.E2E_TENANT_SLUG || 'default';

test.describe('Logout', () => {
  test('can logout from the application', async ({ page }) => {
    // Start on an authenticated page
    await page.goto(`/${tenantSlug}/collections`);
    await page.waitForLoadState('networkidle');

    // Click the user menu / logout button
    const logoutButton = page.getByTestId('logout-button');
    await logoutButton.click();

    // Should be redirected away from the protected page
    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/login`));
  });

  test('redirects to login after logout', async ({ page }) => {
    await page.goto(`/${tenantSlug}/collections`);
    await page.waitForLoadState('networkidle');

    const logoutButton = page.getByTestId('logout-button');
    await logoutButton.click();

    const loginPage = new LoginPage(page, tenantSlug);
    await expect(loginPage.container).toBeVisible();
  });

  test('cannot access protected pages after logout', async ({ page }) => {
    await page.goto(`/${tenantSlug}/collections`);
    await page.waitForLoadState('networkidle');

    const logoutButton = page.getByTestId('logout-button');
    await logoutButton.click();

    // Wait for redirect to login
    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/login`));

    // Try to navigate to a protected page
    await page.goto(`/${tenantSlug}/collections`);

    // Should be redirected back to login
    await expect(page).toHaveURL(new RegExp(`/${tenantSlug}/login`));
  });
});
