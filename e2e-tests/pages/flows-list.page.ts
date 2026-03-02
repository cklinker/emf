import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class FlowsListPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly editButtons: Locator;
  readonly deleteButtons: Locator;
  readonly runButtons: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId('add-flow-button');
    this.table = this.testId('flows-table');
    this.editButtons = this.page.locator('[data-testid^="edit-button-"]');
    this.deleteButtons = this.page.locator('[data-testid^="delete-button-"]');
    this.runButtons = this.page.locator('[data-testid^="run-button-"]');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/flows'));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="flow-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }

  async clickRun(index: number): Promise<void> {
    await this.testId(`run-button-${index}`).click();
  }
}
