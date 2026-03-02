import { test as setup, expect } from '@playwright/test';
import { loginViaAuthentik } from '../helpers/authentik-helper';

const TENANT_SLUG = process.env.E2E_TENANT_SLUG || 'default';
const AUTH_STATE_PATH = './auth/storage-state.json';

setup('authenticate via Authentik', async ({ page }) => {
  // Navigate to the app — it will redirect to Authentik for login
  await page.goto(`/${TENANT_SLUG}/setup`);

  // The app should redirect to Authentik's login page.
  // If there's only one OIDC provider, the app auto-redirects.
  // If multiple providers, the login page shows buttons — click the first provider.
  const isOnAuthentik = page.url().includes('authentik');
  if (!isOnAuthentik) {
    // We're on the app's login page — click the OIDC provider button
    const providerButton = page.locator(
      '[data-testid="oidc-provider-button"], button:has-text("Log in"), button:has-text("Sign in")',
    ).first();
    await providerButton.click({ timeout: 10_000 });
  }

  // Now we're on Authentik's login form — fill in credentials
  await loginViaAuthentik(page, {
    username: process.env.E2E_TEST_USERNAME || 'e2e-admin@emf.local',
    password: process.env.E2E_TEST_PASSWORD || '',
  });

  // Wait for redirect back to the app (callback → setup page)
  await page.waitForURL(`**/${TENANT_SLUG}/**`, { timeout: 30_000 });
  await expect(page).not.toHaveURL(/\/login/);

  // Save the authenticated browser state for all other test projects
  await page.context().storageState({ path: AUTH_STATE_PATH });
});
