import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class MetricsPage extends BasePage {
  readonly container: Locator;
  readonly summaryCards: Locator;
  readonly timeRangeToolbar: Locator;
  readonly routeFilter: Locator;
  readonly chartGrid: Locator;
  readonly requestRateChart: Locator;
  readonly latencyChart: Locator;
  readonly errorsChart: Locator;
  readonly authFailuresChart: Locator;
  readonly rateLimitChart: Locator;
  readonly activeRequestsChart: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("metrics-page");
    this.summaryCards = this.testId("metrics-summary-cards");
    this.timeRangeToolbar = this.testId("metrics-time-range");
    this.routeFilter = this.testId("metrics-route-filter");
    this.chartGrid = this.testId("metrics-chart-grid");
    this.requestRateChart = this.testId("metrics-chart-request-rate");
    this.latencyChart = this.testId("metrics-chart-latency");
    this.errorsChart = this.testId("metrics-chart-errors");
    this.authFailuresChart = this.testId("metrics-chart-auth-failures");
    this.rateLimitChart = this.testId("metrics-chart-rate-limit");
    this.activeRequestsChart = this.testId("metrics-chart-active-requests");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/metrics"));
    await this.waitForLoadingComplete();
  }

  async selectTimeRange(range: string): Promise<void> {
    await this.testId(`metrics-time-range-${range}`).click();
  }

  async selectRoute(route: string): Promise<void> {
    await this.routeFilter.selectOption(route);
  }

  async getSummaryCardCount(): Promise<number> {
    return this.summaryCards
      .locator('[data-testid^="metrics-summary-card-"]')
      .count();
  }

  async getChartCount(): Promise<number> {
    return this.chartGrid
      .locator('[data-testid^="metrics-chart-"]')
      .count();
  }

  async getActiveTimeRange(): Promise<string> {
    const buttons = this.timeRangeToolbar.locator("button");
    const count = await buttons.count();
    for (let i = 0; i < count; i++) {
      const button = buttons.nth(i);
      const classes = await button.getAttribute("class");
      if (classes?.includes("bg-primary")) {
        return (await button.textContent()) ?? "";
      }
    }
    return "";
  }
}
