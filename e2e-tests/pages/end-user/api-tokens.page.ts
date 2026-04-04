import type { Page, Locator } from "@playwright/test";
import { BasePage } from "../base.page";

export class ApiTokensPage extends BasePage {
  readonly container: Locator;
  readonly createButton: Locator;
  readonly tokensTable: Locator;
  readonly createFormModal: Locator;
  readonly tokenNameInput: Locator;
  readonly tokenExpirySelect: Locator;
  readonly createSubmitButton: Locator;
  readonly createdDialog: Locator;
  readonly createdTokenValue: Locator;
  readonly copyTokenButton: Locator;
  readonly createdDoneButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("api-tokens-page");
    this.createButton = this.testId("create-token-button");
    this.tokensTable = this.testId("tokens-table");
    this.createFormModal = this.testId("create-token-form-modal");
    this.tokenNameInput = this.testId("token-name-input");
    this.tokenExpirySelect = this.testId("token-expiry-select");
    this.createSubmitButton = this.testId("create-token-submit");
    this.createdDialog = this.testId("token-created-dialog-modal");
    this.createdTokenValue = this.testId("created-token-value");
    this.copyTokenButton = this.testId("copy-token-button");
    this.createdDoneButton = this.testId("token-created-done");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/app/api-tokens"));
    await this.waitForPageLoad();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillTokenForm(name: string): Promise<void> {
    await this.tokenNameInput.fill(name);
  }

  async submitCreate(): Promise<void> {
    await this.createSubmitButton.click();
  }

  async dismissCreatedDialog(): Promise<void> {
    await this.createdDoneButton.click();
  }

  revokeButton(tokenId: string): Locator {
    return this.testId(`revoke-token-${tokenId}`);
  }
}
