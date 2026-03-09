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
    this.container = this.testId("monitoring-overview-page");
    this.summaryCards = this.testId("monitoring-summary-cards");
    this.timeRangeToolbar = this.testId("overview-time-range");
    this.routeFilter = this.testId("overview-route-filter");
    this.chartGrid = this.testId("overview-chart-grid");
    this.requestRateChart = this.testId("overview-chart-request-rate");
    this.latencyChart = this.testId("overview-chart-latency");
    this.errorsChart = this.testId("overview-chart-errors");
    this.authFailuresChart = this.testId("overview-chart-auth-failures");
    this.rateLimitChart = this.testId("overview-chart-rate-limit");
    this.activeRequestsChart = this.testId("overview-chart-active-requests");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/monitoring/overview"));
    await this.waitForLoadingComplete();
  }

  async selectTimeRange(range: string): Promise<void> {
    await this.testId(`overview-time-range-${range}`).click();
  }

  async selectRoute(route: string): Promise<void> {
    await this.routeFilter.selectOption(route);
  }

  async getSummaryCardCount(): Promise<number> {
    return this.summaryCards
      .locator('[data-testid^="monitoring-summary-"]')
      .count();
  }

  async getChartCount(): Promise<number> {
    return this.chartGrid.locator('[data-testid^="overview-chart-"]').count();
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
