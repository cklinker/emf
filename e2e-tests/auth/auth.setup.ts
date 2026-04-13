import { test as setup, expect } from "@playwright/test";
import { loginViaAuthentik } from "../helpers/authentik-helper";
import { attemptDirectLogin, toSessionTokens } from "../helpers/direct-login";
import fs from "fs";

const TENANT_SLUG = process.env.E2E_TENANT_SLUG || "default";
const AUTH_STATE_PATH = "./auth/storage-state.json";
const SESSION_TOKENS_PATH = "./auth/session-tokens.json";

setup("authenticate via Authentik", async ({ page }) => {
  setup.setTimeout(90_000);

  // --- Strategy 1: Direct API login (avoids browser OIDC redirect issues) ---
  const directLoginUrl =
    process.env.E2E_AUTH_DIRECT_LOGIN_URL ||
    process.env.E2E_AUTH_BASE_URL ||
    "";

  if (directLoginUrl) {
    const username = process.env.E2E_TEST_USERNAME || "e2e-admin@kelta.local";
    const password = process.env.E2E_TEST_PASSWORD || "";

    const result = await attemptDirectLogin({
      authBaseUrl: directLoginUrl,
      username,
      password,
      tenantSlug: TENANT_SLUG,
    });

    if (result) {
      const sessionTokens = toSessionTokens(result);
      fs.writeFileSync(
        SESSION_TOKENS_PATH,
        JSON.stringify(sessionTokens, null, 2),
      );

      // Navigate to the app with tokens pre-injected so we can capture
      // cookies and localStorage for the storage state file.
      await page.addInitScript((tokenMap: Record<string, string>) => {
        for (const [key, value] of Object.entries(tokenMap)) {
          sessionStorage.setItem(key, value);
        }
      }, sessionTokens);

      await page.goto(`/${TENANT_SLUG}/app`, { waitUntil: "load" });

      // Wait for the SPA to recognize the injected tokens and render the app
      await page.waitForURL(
        (url) => {
          const pathname = url.pathname;
          return (
            pathname.startsWith(`/${TENANT_SLUG}/`) &&
            !pathname.includes("/login")
          );
        },
        { timeout: 30_000 },
      );

      await page.context().storageState({ path: AUTH_STATE_PATH });
      return;
    }
  }

  // --- Strategy 2: Browser-based OIDC login (original flow) ---
  // Navigate to the app login page — the SPA will load, check auth, then either
  // show provider buttons or auto-redirect to Authentik (single provider).
  await page.goto(`/${TENANT_SLUG}/login`, { waitUntil: "load" });

  // The SPA may auto-redirect to Authentik if there's only one OIDC provider.
  // Wait until we're either on Authentik or see provider buttons on the login page.
  await page.waitForFunction(
    () => {
      const url = window.location.href;
      // Already redirected to Authentik
      if (url.includes("auth")) return true;
      // Still on app login page — check if provider buttons are rendered
      const buttons = document.querySelectorAll("button");
      return buttons.length > 0;
    },
    { timeout: 30_000 },
  );

  // If we're still on the app's login page, click a provider button to trigger OIDC redirect
  if (!page.url().includes("Internal")) {
    // Wait for the login page to fully render with provider buttons
    await page.waitForSelector('[data-testid="login-page"]', {
      timeout: 10_000,
    });
    // Click the first provider button (contains a KeyRound icon and provider name)
    const providerButton = page
      .locator('[class="btn-primary"] button')
      .first();
    await providerButton.click({ timeout: 10_000 });
  }

  // Wait until we're on the Authentik login page
  await page.waitForURL("**/auth**", { timeout: 30_000 });

  // Now we're on Authentik's login form — fill in credentials
  await loginViaAuthentik(page, {
    username: process.env.E2E_TEST_USERNAME || "e2e-admin@kelta.local",
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

  // Extract sessionStorage tokens — the app stores auth tokens in sessionStorage
  // (not localStorage), and Playwright's storageState only captures cookies + localStorage.
  // We save them separately and inject them via addInitScript in each test.
  const sessionTokens = await page.evaluate(() => {
    const result: Record<string, string> = {};
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i);
      if (key && key.startsWith("kelta_auth")) {
        result[key] = sessionStorage.getItem(key) || "";
      }
    }
    return result;
  });

  fs.writeFileSync(SESSION_TOKENS_PATH, JSON.stringify(sessionTokens, null, 2));

  // Save the authenticated browser state (cookies + localStorage) for all other test projects
  await page.context().storageState({ path: AUTH_STATE_PATH });
});
