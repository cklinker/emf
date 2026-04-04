import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class AnalyticsPage extends BasePage {
  readonly container: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("analytics-page");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/analytics"));
    await this.waitForPageLoad();
  }
}
