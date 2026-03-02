import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class ScriptsPage extends BasePage {
  readonly scriptsPage: Locator;
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formOverlay: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly typeInput: Locator;
  readonly languageInput: Locator;
  readonly sourceCodeInput: Locator;
  readonly activeCheckbox: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.scriptsPage = this.testId('scripts-page');
    this.createButton = this.testId('add-script-button');
    this.table = this.testId('scripts-table');
    this.formOverlay = this.testId('script-form-overlay');
    this.formModal = this.testId('script-form-modal');
    this.nameInput = this.testId('script-name-input');
    this.typeInput = this.testId('script-type-input');
    this.languageInput = this.testId('script-language-input');
    this.sourceCodeInput = this.testId('script-source-code-input');
    this.activeCheckbox = this.testId('script-active-input');
    this.submitButton = this.testId('script-form-submit');
    this.cancelButton = this.testId('script-form-cancel');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/scripts'));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm(data: {
    name?: string;
    type?: string;
    language?: string;
    sourceCode?: string;
  }): Promise<void> {
    if (data.name) await this.nameInput.fill(data.name);
    if (data.type) await this.typeInput.fill(data.type);
    if (data.language) await this.languageInput.fill(data.language);
    if (data.sourceCode) await this.sourceCodeInput.fill(data.sourceCode);
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.table.locator('[data-testid^="script-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`script-row-${index}`)
      .getByRole('button', { name: /edit/i })
      .click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`script-row-${index}`)
      .getByRole('button', { name: /delete/i })
      .click();
  }
}
