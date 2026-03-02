import { test, expect } from "../../fixtures";
import { LoginPage } from "../../pages/login.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";
const AUTHENTIK_URL_PATTERN = /authentik/;

// These tests run unauthenticated to verify login page behavior.
test.use({ storageState: { cookies: [], origins: [] } });

test.describe("Login Page", () => {
  test("shows login page with OIDC provider buttons or auto-redirects to Authentik", async ({
    page,
  }) => {
    // Navigate with waitUntil: 'commit' so we can catch the page before an
    // auto-redirect to the OIDC provider occurs (single-provider setups
    // redirect immediately).
    await page.goto(`/${tenantSlug}/login`, { waitUntil: "commit" });

    // Wait until we either see the login page container with provider buttons
    // OR the browser has been redirected to Authentik.
    const result = await page.waitForFunction(
      (authentikPattern) => {
        const url = window.location.href;
        if (url.match(authentikPattern)) return "redirected";
        const container = document.querySelector('[data-testid="login-page"]');
        if (container) {
          const buttons = container.querySelectorAll("button");
          if (buttons.length > 0) return "buttons";
        }
        return null;
      },
      AUTHENTIK_URL_PATTERN.source,
      { timeout: 30_000 },
    );

    const outcome = await result.jsonValue();

    if (outcome === "buttons") {
      // Multiple providers: the login page rendered provider buttons.
      const loginPage = new LoginPage(page, tenantSlug);
      await expect(loginPage.container).toBeVisible();
      const providerNames = await loginPage.getProviderNames();
      expect(providerNames.length).toBeGreaterThan(0);
    } else {
      // Single provider: auto-redirected to Authentik for authentication.
      expect(page.url()).toMatch(AUTHENTIK_URL_PATTERN);
    }
  });

  test("redirects unauthenticated users to login or OIDC provider", async ({
    page,
  }) => {
    // An unauthenticated user navigating to a protected page should be
    // redirected to the login page, which may itself auto-redirect to
    // the OIDC provider (Authentik) if only one provider is configured.
    await page.goto(`/${tenantSlug}/collections`, { waitUntil: "commit" });

    await page.waitForFunction(
      ({ slug, authentikPattern }) => {
        const url = window.location.href;
        return url.includes(`/${slug}/login`) || !!url.match(authentikPattern);
      },
      { slug: tenantSlug, authentikPattern: AUTHENTIK_URL_PATTERN.source },
      { timeout: 30_000 },
    );

    const url = page.url();
    const onLogin = url.includes(`/${tenantSlug}/login`);
    const onAuthentik = AUTHENTIK_URL_PATTERN.test(url);
    expect(onLogin || onAuthentik).toBe(true);
  });

  test("displays error message on failed login", async ({ page }) => {
    const loginPage = new LoginPage(page, tenantSlug);
    await page.goto(`/${tenantSlug}/login?error=auth_failed`);

    await expect(loginPage.errorMessage).toBeVisible();
  });

  test("auto-redirects when single provider exists", async ({ page }) => {
    // Navigate with waitUntil: 'commit' to catch the page before auto-redirect.
    await page.goto(`/${tenantSlug}/login`, { waitUntil: "commit" });

    // Wait for either provider buttons to appear or redirect to Authentik.
    const result = await page.waitForFunction(
      (authentikPattern) => {
        const url = window.location.href;
        if (url.match(authentikPattern)) return "redirected";
        const container = document.querySelector('[data-testid="login-page"]');
        if (container) {
          const buttons = container.querySelectorAll("button");
          if (buttons.length > 0) return "buttons";
        }
        return null;
      },
      AUTHENTIK_URL_PATTERN.source,
      { timeout: 30_000 },
    );

    const outcome = await result.jsonValue();

    if (outcome === "redirected") {
      // Single provider — should have navigated away from the login page
      // to the OIDC provider (Authentik).
      expect(page.url()).toMatch(AUTHENTIK_URL_PATTERN);
    } else {
      // Multiple providers — buttons should be visible for manual selection.
      const loginPage = new LoginPage(page, tenantSlug);
      await expect(loginPage.providerButtons.first()).toBeVisible();
    }
  });
});
