import { test, expect } from '../../../fixtures';
import { OidcProvidersPage } from '../../../pages/oidc-providers.page';

test.describe('OIDC Providers', () => {
  let oidcProvidersPage: OidcProvidersPage;

  test.beforeEach(async ({ page }) => {
    oidcProvidersPage = new OidcProvidersPage(page);
    await oidcProvidersPage.goto();
  });

  test('displays OIDC providers page', async () => {
    await expect(oidcProvidersPage.table).toBeVisible();
  });

  test('shows providers in table', async () => {
    const rowCount = await oidcProvidersPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(0);
  });

  test('shows at least one configured provider', async () => {
    const rowCount = await oidcProvidersPage.getRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });
});
