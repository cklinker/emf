import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class WebhooksPage extends BasePage {
  readonly webhooksPage: Locator;
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formOverlay: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly urlInput: Locator;
  readonly eventsInput: Locator;
  readonly activeCheckbox: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.webhooksPage = this.testId('webhooks-page');
    this.createButton = this.testId('add-webhook-button');
    this.table = this.testId('webhooks-table');
    this.formOverlay = this.testId('webhook-form-overlay');
    this.formModal = this.testId('webhook-form-modal');
    this.nameInput = this.testId('webhook-name-input');
    this.urlInput = this.testId('webhook-url-input');
    this.eventsInput = this.testId('webhook-events-input');
    this.activeCheckbox = this.testId('webhook-active-input');
    this.submitButton = this.testId('webhook-form-submit');
    this.cancelButton = this.testId('webhook-form-cancel');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/webhooks'));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm(data: {
    name?: string;
    url?: string;
    events?: string;
  }): Promise<void> {
    if (data.name) await this.nameInput.fill(data.name);
    if (data.url) await this.urlInput.fill(data.url);
    if (data.events) await this.eventsInput.fill(data.events);
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.table.locator('[data-testid^="webhook-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`webhook-row-${index}`)
      .getByRole('button', { name: /edit/i })
      .click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`webhook-row-${index}`)
      .getByRole('button', { name: /delete/i })
      .click();
  }
}
