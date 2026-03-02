import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class PageBuilderPage extends BasePage {
  readonly pageBuilderPage: Locator;
  readonly table: Locator;
  readonly createButton: Locator;
  readonly editorCanvas: Locator;
  readonly componentPalette: Locator;
  readonly propertyPanel: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.pageBuilderPage = this.testId('page-builder-page');
    this.table = this.testId('page-builder-table');
    this.createButton = this.testId('page-builder-create-button');
    this.editorCanvas = this.testId('page-builder-editor-canvas');
    this.componentPalette = this.testId('page-builder-component-palette');
    this.propertyPanel = this.testId('page-builder-property-panel');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/pages'));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.table.locator('tbody tr, [role="row"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.table
      .locator('tbody tr, [role="row"]')
      .nth(index)
      .getByRole('button', { name: /edit/i })
      .click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.table
      .locator('tbody tr, [role="row"]')
      .nth(index)
      .getByRole('button', { name: /delete/i })
      .click();
  }
}
