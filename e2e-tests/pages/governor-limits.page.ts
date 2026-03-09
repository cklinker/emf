import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class GovernorLimitsPage extends BasePage {
  readonly governorLimitsPage: Locator;
  readonly heading: Locator;
  readonly editButton: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;
  readonly limitsTable: Locator;
  readonly metricCards: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.governorLimitsPage = this.testId("governor-limits-page");
    this.heading = page.getByRole("heading", { name: /governor limits/i });
    this.editButton = this.testId("governor-limits-edit-button");
    this.saveButton = this.testId("governor-limits-save-button");
    this.cancelButton = this.testId("governor-limits-cancel-button");
    this.limitsTable = this.testId("governor-limits-table");
    this.metricCards = this.testId("governor-limits-metric-cards");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/governor-limits"));
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
    return this.metricCards.locator('[data-testid^="metric-card-"]').count();
  }

  metricCard(index: number): Locator {
    return this.testId(`metric-card-${index}`);
  }

  limitRow(key: string): Locator {
    return this.testId(`limit-row-${key}`);
  }

  limitInput(key: string): Locator {
    return this.testId(`limit-input-${key}`);
  }

  async getLimitRowCount(): Promise<number> {
    return this.limitsTable.locator("tbody tr").count();
  }
}
