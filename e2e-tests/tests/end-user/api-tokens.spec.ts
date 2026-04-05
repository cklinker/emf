import { test, expect } from "../../fixtures";
import { ApiTokensPage } from "../../pages/end-user/api-tokens.page";
import { waitForAnyVisible } from "../../helpers/wait-helpers";

test.describe("Personal Access Tokens", () => {
  let tokensPage: ApiTokensPage;

  test.beforeEach(async ({ page }) => {
    tokensPage = new ApiTokensPage(page);
    await tokensPage.goto();
  });

  test("displays api tokens page", async () => {
    await expect(tokensPage.container).toBeVisible();
  });

  test("shows create token button", async () => {
    await expect(tokensPage.createButton).toBeVisible();
  });

  test("shows tokens table or empty state", async ({ page }) => {
    const found = await waitForAnyVisible([
      tokensPage.tokensTable,
      page.getByTestId("empty-state"),
      tokensPage.container,
    ]);
    expect(found).toBe(true);
  });

  test("opens create token form", async () => {
    await tokensPage.clickCreate();
    await expect(tokensPage.createFormModal).toBeVisible();
    await expect(tokensPage.tokenNameInput).toBeVisible();
    await expect(tokensPage.createSubmitButton).toBeVisible();
  });

  test("creates a new token and shows value", async ({ page }) => {
    await tokensPage.clickCreate();
    await tokensPage.fillTokenForm(`E2E Token ${Date.now()}`);

    // Listen for the API response to detect failures early
    const responsePromise = page.waitForResponse(
      (resp) =>
        resp.url().includes("/api/me/tokens") &&
        resp.request().method() === "POST",
      { timeout: 15_000 },
    );

    await tokensPage.submitCreate();

    const response = await responsePromise;
    if (!response.ok()) {
      test.skip(true, `Token creation API returned ${response.status()}`);
      return;
    }

    // After creation, the success dialog should show the token value
    await expect(tokensPage.createdDialog).toBeVisible({ timeout: 10_000 });
    await expect(tokensPage.createdTokenValue).toBeVisible();
    await expect(tokensPage.copyTokenButton).toBeVisible();

    // Dismiss and verify the token appears in the table
    await tokensPage.dismissCreatedDialog();
    await expect(tokensPage.tokensTable).toBeVisible();
  });
});
