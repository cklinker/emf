import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class GovernorLimitsPage extends BasePage {
  readonly governorLimitsPage: Locator;
  readonly heading: Locator;
  readonly tierBadge: Locator;
  readonly tierSelect: Locator;
  readonly tierIndicator: Locator;
  readonly editButton: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;
  readonly limitsTable: Locator;
  readonly metricCards: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.governorLimitsPage = this.testId("governor-limits-page");
    this.heading = page.getByRole("heading", { name: /governor limits/i });
    this.tierBadge = this.testId("governor-limits-tier-badge");
    this.tierSelect = this.testId("governor-limits-tier-select");
    // Either renders depending on user permissions: non-admin sees a static
    // badge, admin (MANAGE_TENANTS) sees an editable select. The CSS `,` is
    // an OR — Playwright resolves to whichever exists.
    this.tierIndicator = page.locator(
      '[data-testid="governor-limits-tier-badge"], [data-testid="governor-limits-tier-select"]'
    );
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
