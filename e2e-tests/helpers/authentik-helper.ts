/**
 * Authentik OIDC helper for e2e test authentication.
 *
 * Provides browser-based login through Authentik's login form,
 * which works with any Authentik configuration without requiring
 * special grant types.
 */

import type { Page } from '@playwright/test';

export interface AuthentikLoginOptions {
  username: string;
  password: string;
}

/**
 * Complete the Authentik login form in the browser.
 *
 * After the app redirects to Authentik's authorization endpoint,
 * this function fills in the username/password and submits the form.
 * Authentik will then redirect back to the app's callback URL.
 */
export async function loginViaAuthentik(
  page: Page,
  options: AuthentikLoginOptions,
): Promise<void> {
  // Authentik shows a username/identifier field first
  const usernameInput = page.locator(
    'input[name="uidField"], input[name="username"], input[type="text"]',
  ).first();
  await usernameInput.waitFor({ state: 'visible', timeout: 30_000 });
  await usernameInput.fill(options.username);

  // Click the submit/continue button to proceed to password
  const continueButton = page.locator(
    'button[type="submit"], .pf-c-button--primary, button.ak-button',
  ).first();
  await continueButton.click();

  // Authentik then shows the password field
  const passwordInput = page.locator(
    'input[name="password"], input[type="password"]',
  ).first();
  await passwordInput.waitFor({ state: 'visible', timeout: 15_000 });
  await passwordInput.fill(options.password);

  // Submit the login form
  const loginButton = page.locator(
    'button[type="submit"], .pf-c-button--primary, button.ak-button',
  ).first();
  await loginButton.click();

  // If there's a consent screen, approve it
  try {
    const consentButton = page.locator(
      'button[type="submit"]:has-text("Continue"), button:has-text("Allow"), button:has-text("Approve")',
    ).first();
    await consentButton.click({ timeout: 5_000 });
  } catch {
    // No consent screen — that's fine
  }
}
