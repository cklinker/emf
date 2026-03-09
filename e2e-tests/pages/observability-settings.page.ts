import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class ObservabilitySettingsPage extends BasePage {
  readonly traceRetentionInput: Locator;
  readonly logRetentionInput: Locator;
  readonly auditRetentionInput: Locator;
  readonly saveButton: Locator;
  readonly successMessage: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.traceRetentionInput = this.testId("obs-settings-trace-retention");
    this.logRetentionInput = this.testId("obs-settings-log-retention");
    this.auditRetentionInput = this.testId("obs-settings-audit-retention");
    this.saveButton = this.testId("obs-settings-save");
    this.successMessage = this.testId("obs-settings-success");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/monitoring/settings"));
    await this.waitForLoadingComplete();
  }

  async setTraceRetention(days: number): Promise<void> {
    await this.traceRetentionInput.fill(String(days));
  }

  async setLogRetention(days: number): Promise<void> {
    await this.logRetentionInput.fill(String(days));
  }

  async setAuditRetention(days: number): Promise<void> {
    await this.auditRetentionInput.fill(String(days));
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }
}
