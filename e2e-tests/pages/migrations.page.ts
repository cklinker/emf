import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class MigrationsPage extends BasePage {
  readonly migrationsPage: Locator;
  readonly historyTab: Locator;
  readonly planTab: Locator;
  readonly executionTab: Locator;
  readonly historyTable: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.migrationsPage = this.testId("migrations-page");
    this.historyTab = this.testId("migrations-history-tab");
    this.planTab = this.testId("migrations-plan-tab");
    this.executionTab = this.testId("migrations-execution-tab");
    this.historyTable = this.testId("migrations-history-table");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/migrations"));
    await this.waitForLoadingComplete();
  }

  async clickHistoryTab(): Promise<void> {
    await this.historyTab.click();
  }

  async clickPlanTab(): Promise<void> {
    await this.planTab.click();
  }

  async getRowCount(): Promise<number> {
    return this.historyTable.locator('tbody tr, [role="row"]').count();
  }
}
