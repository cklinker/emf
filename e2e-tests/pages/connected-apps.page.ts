import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class ConnectedAppsPage extends BasePage {
  readonly connectedAppsPage: Locator;
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formOverlay: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly descriptionInput: Locator;
  readonly scopesInput: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;
  readonly credentialsDialog: Locator;
  readonly credentialsClientId: Locator;
  readonly credentialsClientSecret: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.connectedAppsPage = this.testId('connected-apps-page');
    this.createButton = this.testId('add-connected-app-button');
    this.table = this.testId('connected-apps-table');
    this.formOverlay = this.testId('connected-app-form-overlay');
    this.formModal = this.testId('connected-app-form-modal');
    this.nameInput = this.testId('connected-app-name-input');
    this.descriptionInput = this.testId('connected-app-description-input');
    this.scopesInput = this.testId('connected-app-scopes-input');
    this.submitButton = this.testId('connected-app-form-submit');
    this.cancelButton = this.testId('connected-app-form-cancel');
    this.credentialsDialog = this.testId('credentials-dialog-modal');
    this.credentialsClientId = this.testId('credentials-client-id');
    this.credentialsClientSecret = this.testId('credentials-client-secret');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/connected-apps'));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm(data: {
    name?: string;
    description?: string;
    scopes?: string;
  }): Promise<void> {
    if (data.name) await this.nameInput.fill(data.name);
    if (data.description) await this.descriptionInput.fill(data.description);
    if (data.scopes) await this.scopesInput.fill(data.scopes);
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.table
      .locator('[data-testid^="connected-app-row-"]')
      .count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`connected-app-row-${index}`)
      .getByRole('button', { name: /edit/i })
      .click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`connected-app-row-${index}`)
      .getByRole('button', { name: /delete/i })
      .click();
  }

  async getCredentials(): Promise<{
    clientId: string | null;
    clientSecret: string | null;
  }> {
    const clientId = await this.credentialsClientId.textContent();
    const clientSecret = await this.credentialsClientSecret.textContent();
    return { clientId, clientSecret };
  }
}
