import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class ReportsPage extends BasePage {
  readonly reportsPage: Locator;
  readonly createButton: Locator;
  readonly reportList: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.reportsPage = this.testId("reports-page");
    this.createButton = this.testId("reports-create-button");
    this.reportList = this.testId("reports-list");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/reports"));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async getReportCount(): Promise<number> {
    return this.reportList.locator('[data-testid^="report-"]').count();
  }
}
