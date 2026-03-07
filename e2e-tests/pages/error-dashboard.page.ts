import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class ErrorDashboardPage extends BasePage {
  readonly topErrorsTable: Locator;
  readonly errorRows: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.topErrorsTable = this.testId("error-dashboard-table");
    this.errorRows = this.page.locator('[data-testid^="error-dashboard-row-"]');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/errors"));
    await this.waitForLoadingComplete();
  }

  async getRowCount(): Promise<number> {
    return this.errorRows.count();
  }
}
