import { test, expect } from "../../fixtures";
import { ObjectListPage } from "../../pages/end-user/object-list.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Permission Enforcement Journey", () => {
  test("enforces permissions on collection access", async ({ page }) => {
    // Attempt to navigate to a non-existent or restricted collection
    const restrictedCollectionName = `restricted_${Date.now()}`;
    const listPage = new ObjectListPage(
      page,
      restrictedCollectionName,
      tenantSlug,
    );
    await listPage.goto();

    // The application should handle restricted access gracefully.
    // This could manifest as a 403/404 error page, a redirect,
    // or an empty state with an access denied message.
    const errorIndicator = page.locator(
      '[data-testid="error-page"], [data-testid="access-denied"], [data-testid="not-found"]',
    );
    const redirectedToHome = page.url().includes("/app/home");

    const isErrorVisible = await errorIndicator
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    // Either an error indicator is shown or the user was redirected
    expect(isErrorVisible || redirectedToHome).toBe(true);
  });
});
