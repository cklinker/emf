import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class RequestLogPage extends BasePage {
  readonly methodFilter: Locator;
  readonly statusFilter: Locator;
  readonly pathSearch: Locator;
  readonly dateRange: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly pagination: Locator;
  readonly expandedDetail: Locator;
  readonly requestBody: Locator;
  readonly responseBody: Locator;
  readonly traceLink: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.methodFilter = this.testId("request-log-method-filter");
    this.statusFilter = this.testId("request-log-status-filter");
    this.pathSearch = this.testId("request-log-path-search");
    this.dateRange = this.testId("request-log-date-range");
    this.table = this.testId("request-log-table");
    this.rows = this.page.locator('[data-testid^="request-log-row-"]');
    this.pagination = this.testId("request-log-pagination");
    this.expandedDetail = this.testId("request-log-expanded-detail");
    this.requestBody = this.testId("request-log-request-body");
    this.responseBody = this.testId("request-log-response-body");
    this.traceLink = this.testId("request-log-trace-link");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/request-log"));
    await this.waitForLoadingComplete();
  }

  async filterByMethod(method: string): Promise<void> {
    await this.methodFilter.selectOption(method);
  }

  async filterByStatus(status: string): Promise<void> {
    await this.statusFilter.selectOption(status);
  }

  async searchByPath(path: string): Promise<void> {
    await this.pathSearch.fill(path);
  }

  async selectDateRange(range: string): Promise<void> {
    await this.dateRange.getByText(range, { exact: true }).click();
  }

  async expandRow(index: number): Promise<void> {
    await this.rows.nth(index).click();
  }

  async getRowCount(): Promise<number> {
    return this.rows.count();
  }
}
