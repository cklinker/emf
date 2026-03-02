import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class TenantDashboardPage extends BasePage {
  readonly tenantDashboardPage: Locator;
  readonly usageCards: Locator;
  readonly usageApiCalls: Locator;
  readonly usageStorage: Locator;
  readonly usageUsers: Locator;
  readonly usageCollections: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.tenantDashboardPage = this.testId("tenant-dashboard-page");
    this.usageCards = this.testId("usage-cards");
    this.usageApiCalls = this.testId("usage-api-calls");
    this.usageStorage = this.testId("usage-storage");
    this.usageUsers = this.testId("usage-users");
    this.usageCollections = this.testId("usage-collections");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/tenant-dashboard"));
    await this.waitForLoadingComplete();
  }

  async getUsageCardCount(): Promise<number> {
    return this.usageCards.locator('[data-testid^="usage-"]').count();
  }

  async getApiCallsUsage(): Promise<string | null> {
    return this.usageApiCalls.textContent();
  }

  async getStorageUsage(): Promise<string | null> {
    return this.usageStorage.textContent();
  }
}
