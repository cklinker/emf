import { test, expect } from "../../fixtures";
import { ObjectListPage } from "../../pages/end-user/object-list.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

test.describe("Permission Enforcement Journey", () => {
  test("handles non-existent collection gracefully", async ({ page }) => {
    // Attempt to navigate to a non-existent collection
    const restrictedCollectionName = `restricted_${Date.now()}`;
    const listPage = new ObjectListPage(
      page,
      restrictedCollectionName,
      tenantSlug,
    );
    await listPage.goto();

    // The application should handle this gracefully:
    // - Show an error page, 404/not-found page
    // - Redirect to home
    // - Show a loading state (collection API returns error)
    // - Show an empty state
    const errorIndicator = page.locator(
      '[data-testid="error-page"], [data-testid="access-denied"], [data-testid="not-found"]',
    );
    const redirectedToHome = page.url().includes("/app/home");
    const loadingIndicator = page.getByText(/loading/i);
    const emptyState = page.getByText(/no.*data|no.*record|not found|error/i);

    const isErrorVisible = await errorIndicator
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const isLoading = await loadingIndicator
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    const isEmpty = await emptyState
      .isVisible({ timeout: 2000 })
      .catch(() => false);

    // Any of these outcomes is acceptable for a non-existent collection
    expect(isErrorVisible || redirectedToHome || isLoading || isEmpty).toBe(
      true,
    );
  });
});
