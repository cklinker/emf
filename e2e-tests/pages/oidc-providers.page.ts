import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class OidcProvidersPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formModal: Locator;
  readonly testConnectionButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId("add-provider-button");
    this.table = this.testId("providers-table");
    this.formModal = this.testId("oidc-form-modal");
    this.testConnectionButton = this.page.locator(
      '[data-testid^="test-button-"]',
    );
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/oidc-providers"));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="provider-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }
}
