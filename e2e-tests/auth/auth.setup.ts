import { test as setup, expect } from "@playwright/test";
import { loginViaAuthentik } from "../helpers/authentik-helper";

const TENANT_SLUG = process.env.E2E_TENANT_SLUG || "default";
const AUTH_STATE_PATH = "./auth/storage-state.json";

setup("authenticate via Authentik", async ({ page }) => {
  setup.setTimeout(90_000);

  // Navigate to the app login page — the SPA will load, check auth, then either
  // show provider buttons or auto-redirect to Authentik (single provider).
  await page.goto(`/${TENANT_SLUG}/login`, { waitUntil: "networkidle" });

  // The SPA may auto-redirect to Authentik if there's only one OIDC provider.
  // Wait until we're either on Authentik or see provider buttons on the login page.
  await page.waitForFunction(
    () => {
      const url = window.location.href;
      // Already redirected to Authentik
      if (url.includes("authentik")) return true;
      // Still on app login page — check if provider buttons are rendered
      const buttons = document.querySelectorAll("button");
      return buttons.length > 0;
    },
    { timeout: 30_000 },
  );

  // If we're still on the app's login page, click a provider button to trigger OIDC redirect
  if (!page.url().includes("authentik")) {
    // Wait for the login page to fully render with provider buttons
    await page.waitForSelector('[data-testid="login-page"]', {
      timeout: 10_000,
    });
    // Click the first provider button (contains a KeyRound icon and provider name)
    const providerButton = page
      .locator('[data-testid="login-page"] button')
      .first();
    await providerButton.click({ timeout: 10_000 });
  }

  // Wait until we're on the Authentik login page
  await page.waitForURL("**/authentik**", { timeout: 30_000 });

  // Now we're on Authentik's login form — fill in credentials
  await loginViaAuthentik(page, {
    username: process.env.E2E_TEST_USERNAME || "e2e-admin@emf.local",
    password: process.env.E2E_TEST_PASSWORD || "",
  });

  // Wait for redirect back to the app after OIDC callback processing.
  // The callback URL is /:tenantSlug/auth/callback, then the app redirects
  // to the originally requested page or /app.
  await page.waitForURL(
    (url) => {
      const pathname = url.pathname;
      return (
        pathname.startsWith(`/${TENANT_SLUG}/`) &&
        !pathname.includes("/auth/callback") &&
        !pathname.includes("/login")
      );
    },
    { timeout: 60_000 },
  );

  await expect(page).not.toHaveURL(/\/login/);

  // Save the authenticated browser state for all other test projects
  await page.context().storageState({ path: AUTH_STATE_PATH });
});
