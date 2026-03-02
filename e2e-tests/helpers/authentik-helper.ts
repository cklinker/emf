/**
 * Authentik OIDC helper for e2e test authentication.
 *
 * Provides browser-based login through Authentik's login form,
 * which works with any Authentik configuration without requiring
 * special grant types.
 *
 * Authentik uses Lit web components with open Shadow DOM.
 * Playwright's CSS locators pierce shadow DOM by default, so standard
 * selectors work for reaching inputs inside <ak-stage-identification>
 * and <ak-stage-password>.
 */

import type { Page } from "@playwright/test";

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
 *
 * Authentik's default flow has two stages:
 * 1. Identification stage — username/email input
 * 2. Password stage — password input
 */
export async function loginViaAuthentik(
  page: Page,
  options: AuthentikLoginOptions,
): Promise<void> {
  // Stage 1: Identification — fill in username/email
  // Authentik renders this inside <ak-stage-identification> shadow DOM.
  // Playwright pierces open shadow DOM by default.
  const usernameInput = page.locator('input[name="uidField"]');
  await usernameInput.waitFor({ state: "visible", timeout: 30_000 });
  await usernameInput.click();
  // Use pressSequentially instead of fill — Authentik uses Lit web components
  // whose reactive properties don't update from programmatic .fill() value changes.
  // pressSequentially fires individual key events that Lit's event listeners detect.
  await usernameInput.pressSequentially(options.username, { delay: 20 });

  // Submit the identification stage — pressing Enter is more reliable
  // than clicking the submit button through shadow DOM layers.
  await usernameInput.press("Enter");

  // Stage 2: Password — wait for the password stage to render.
  // After submitting the username, Authentik replaces the stage component,
  // so we need to wait for the new password input to appear.
  const passwordInput = page.locator('input[name="password"]');
  await passwordInput.waitFor({ state: "visible", timeout: 15_000 });
  await passwordInput.click();
  await passwordInput.pressSequentially(options.password, { delay: 20 });

  // Small delay to let Lit process the input events before submitting
  await page.waitForTimeout(200);

  // Submit the password stage
  await passwordInput.press("Enter");

  // Handle optional consent screen — Authentik may show a consent prompt
  // if the application requires explicit consent approval.
  try {
    const consentSubmit = page.locator('button[type="submit"]').first();
    await consentSubmit.click({ timeout: 3_000 });
  } catch {
    // No consent screen — that's expected for most configurations
  }
}
