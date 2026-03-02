import { test, expect } from '../../fixtures';

const tenantSlug = process.env.E2E_TENANT_SLUG || 'default';

test.describe('Unauthorized Page', () => {
  test('displays unauthorized page for restricted access', async ({ page }) => {
    await page.goto(`/${tenantSlug}/unauthorized`);
    await page.waitForLoadState('networkidle');

    const unauthorizedContainer = page.getByTestId('unauthorized-page');
    await expect(unauthorizedContainer).toBeVisible();
  });

  test('shows go home button that navigates back', async ({ page }) => {
    await page.goto(`/${tenantSlug}/unauthorized`);
    await page.waitForLoadState('networkidle');

    const goHomeButton = page.getByRole('button', { name: /home/i });
    await expect(goHomeButton).toBeVisible();

    await goHomeButton.click();

    // Should navigate to the tenant home/dashboard
    await expect(page).not.toHaveURL(new RegExp(`/${tenantSlug}/unauthorized`));
  });

  test('shows go back button', async ({ page }) => {
    // Navigate to a known page first, then to unauthorized
    await page.goto(`/${tenantSlug}/collections`);
    await page.waitForLoadState('networkidle');

    await page.goto(`/${tenantSlug}/unauthorized`);
    await page.waitForLoadState('networkidle');

    const goBackButton = page.getByRole('button', { name: /back/i });
    await expect(goBackButton).toBeVisible();

    await goBackButton.click();

    // Should navigate away from the unauthorized page
    await expect(page).not.toHaveURL(new RegExp(`/${tenantSlug}/unauthorized`));
  });
});
