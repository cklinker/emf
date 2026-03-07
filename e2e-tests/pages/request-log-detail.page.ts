import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class RequestLogDetailPage extends BasePage {
  readonly summaryCard: Locator;
  readonly requestTab: Locator;
  readonly responseTab: Locator;
  readonly traceTab: Locator;
  readonly logsTab: Locator;
  readonly auditTab: Locator;
  readonly jaegerLink: Locator;
  readonly requestHeaders: Locator;
  readonly requestBodyViewer: Locator;
  readonly responseBodyViewer: Locator;
  readonly logEntries: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.summaryCard = this.testId("request-detail-summary");
    this.requestTab = this.testId("request-detail-request-tab");
    this.responseTab = this.testId("request-detail-response-tab");
    this.traceTab = this.testId("request-detail-trace-tab");
    this.logsTab = this.testId("request-detail-logs-tab");
    this.auditTab = this.testId("request-detail-audit-tab");
    this.jaegerLink = this.testId("request-detail-jaeger-link");
    this.requestHeaders = this.testId("request-detail-request-headers");
    this.requestBodyViewer = this.testId("request-detail-request-body");
    this.responseBodyViewer = this.testId("request-detail-response-body");
    this.logEntries = this.page.locator('[data-testid^="request-detail-log-"]');
  }

  async goto(traceId: string): Promise<void> {
    await this.page.goto(this.tenantUrl(`/request-log/${traceId}`));
    await this.waitForLoadingComplete();
  }
}
