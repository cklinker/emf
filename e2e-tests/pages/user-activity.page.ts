import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class UserActivityPage extends BasePage {
  readonly userSelect: Locator;
  readonly timeline: Locator;
  readonly entries: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.userSelect = this.testId("user-activity-user-select");
    this.timeline = this.testId("user-activity-timeline");
    this.entries = this.page.locator('[data-testid^="user-activity-entry-"]');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/monitoring/activity"));
    await this.waitForLoadingComplete();
  }

  async selectUser(userId: string): Promise<void> {
    await this.userSelect.selectOption(userId);
  }

  async getEntryCount(): Promise<number> {
    return this.entries.count();
  }
}
