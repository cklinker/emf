import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class GovernorLimitsPage extends BasePage {
  readonly governorLimitsPage: Locator;
  readonly editButton: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;
  readonly limitsTable: Locator;
  readonly metricCards: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.governorLimitsPage = this.testId('governor-limits-page');
    this.editButton = this.testId('governor-limits-edit-button');
    this.saveButton = this.testId('governor-limits-save-button');
    this.cancelButton = this.testId('governor-limits-cancel-button');
    this.limitsTable = this.testId('governor-limits-table');
    this.metricCards = this.testId('governor-limits-metric-cards');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/governor-limits'));
    await this.waitForLoadingComplete();
  }

  async clickEdit(): Promise<void> {
    await this.editButton.click();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
  }

  async getMetricCardCount(): Promise<number> {
    return this.metricCards
      .locator('[data-testid^="metric-card-"]')
      .count();
  }
}
