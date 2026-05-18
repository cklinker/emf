/**
 * Internal kelta-auth login helper for e2e test authentication.
 *
 * The deployed and docker-compose environments do NOT broker to Authentik for
 * the e2e tenant — the SPA redirects (via the internal OIDC provider) to
 * kelta-auth's own server-rendered login form. That form is plain server-side
 * Thymeleaf (no web components / shadow DOM), so standard selectors and
 * `fill()` work.
 *
 * Form contract (kelta-auth `templates/login.html`):
 *   <form th:action="@{/login}" method="post">
 *     <input type="text"     id="username" name="username" ...>
 *     <input type="password" id="password" name="password" ...>
 *     <button type="submit" class="btn-primary">Sign In</button>
 */

import type { Page } from "@playwright/test";

export interface InternalLoginOptions {
  username: string;
  password: string;
}

/**
 * Complete the kelta-auth internal login form.
 *
 * Waits for the username field (the SPA auto-redirects to this form for a
 * single internal provider, which may take a moment in a deployed env),
 * fills credentials, submits, and dismisses an optional consent screen.
 * Returning to the app is awaited by the caller.
 */
export async function loginViaInternalForm(
  page: Page,
  options: InternalLoginOptions,
): Promise<void> {
  // The SPA login page may briefly render before auto-login redirects to
  // kelta-auth. If it stalls there with a provider button, click it to nudge
  // the redirect; otherwise the auto-login effect handles it.
  const providerButton = page
    .locator('[data-testid="login-page"] button[type="button"]')
    .last();
  try {
    await providerButton.click({ timeout: 3_000 });
  } catch {
    // No SPA provider button (already redirected / auto-login in flight).
  }

  const usernameInput = page.locator("#username");
  await usernameInput.waitFor({ state: "visible", timeout: 45_000 });
  await usernameInput.fill(options.username);

  await page.locator("#password").fill(options.password);

  await page.locator('button[type="submit"]').click();

  // kelta-auth may present an OAuth2 consent screen on first authorization.
  try {
    const consentSubmit = page.locator('button[type="submit"]');
    await consentSubmit.waitFor({ state: "visible", timeout: 3_000 });
    await consentSubmit.click();
  } catch {
    // No consent screen — expected for trusted first-party clients.
  }
}
