import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class EndpointPerformancePage extends BasePage {
  readonly leaderboard: Locator;
  readonly rows: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.leaderboard = this.testId("endpoint-performance-table");
    this.rows = this.page.locator('[data-testid^="endpoint-performance-row-"]');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/monitoring/performance"));
    await this.waitForLoadingComplete();
  }

  async waitForContentLoaded(): Promise<void> {
    const tableOrEmpty = this.leaderboard.or(
      this.page.getByText(/no endpoint performance data/i),
    );
    await this.waitForContentReady(tableOrEmpty);
  }

  async getRowCount(): Promise<number> {
    return this.rows.count();
  }
}
