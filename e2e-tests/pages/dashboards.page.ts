import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class DashboardsPage extends BasePage {
  readonly dashboardsPage: Locator;
  readonly createButton: Locator;
  readonly dashboardList: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.dashboardsPage = this.testId("dashboards-page");
    this.createButton = this.testId("dashboards-create-button");
    this.dashboardList = this.testId("dashboards-list");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/dashboards"));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async getDashboardCount(): Promise<number> {
    return this.dashboardList.locator('[data-testid^="dashboard-"]').count();
  }
}
