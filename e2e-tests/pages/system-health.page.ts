import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class SystemHealthPage extends BasePage {
  readonly dashboardPage: Locator;
  readonly dashboardControls: Locator;
  readonly timeRangeSelector: Locator;
  readonly refreshIntervalSelector: Locator;
  readonly healthCards: Locator;
  readonly metricsCards: Locator;
  readonly metricsRequestRate: Locator;
  readonly metricsErrorRate: Locator;
  readonly metricsLatencyP50: Locator;
  readonly metricsLatencyP99: Locator;
  readonly errorsList: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.dashboardPage = this.testId("dashboard-page");
    this.dashboardControls = this.testId("dashboard-controls");
    this.timeRangeSelector = this.testId("time-range-selector");
    this.refreshIntervalSelector = this.testId("refresh-interval-selector");
    this.healthCards = this.testId("health-cards");
    this.metricsCards = this.testId("metrics-cards");
    this.metricsRequestRate = this.testId("metrics-request-rate");
    this.metricsErrorRate = this.testId("metrics-error-rate");
    this.metricsLatencyP50 = this.testId("metrics-latency-p50");
    this.metricsLatencyP99 = this.testId("metrics-latency-p99");
    this.errorsList = this.testId("errors-list");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/system-health"));
    await this.waitForLoadingComplete();
  }

  async getHealthCardCount(): Promise<number> {
    return this.healthCards.locator('[data-testid^="health-card-"]').count();
  }

  async getHealthStatus(index: number): Promise<string | null> {
    return this.testId(`health-card-${index}`).textContent();
  }

  async setTimeRange(range: string): Promise<void> {
    await this.timeRangeSelector.click();
    await this.page.getByRole("option", { name: range }).click();
  }

  async getErrorCount(): Promise<number> {
    return this.errorsList.locator('li, tr, [role="row"]').count();
  }
}
