import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class MonitoringOverviewPage extends BasePage {
  readonly container: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    // The monitoring overview uses the same page structure as other monitoring pages
    this.container = this.page.locator('[data-testid="monitoring-overview-page"], [data-testid="monitoring-page"]').first();
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/monitoring/overview"));
    await this.waitForPageLoad();
  }
}
