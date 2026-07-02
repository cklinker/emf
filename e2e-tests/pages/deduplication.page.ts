import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

/** Page object for the admin Deduplicate Records workbench (`/:tenant/dedup`). */
export class DeduplicationPage extends BasePage {
  readonly container: Locator;
  readonly collectionSelect: Locator;
  readonly matchFields: Locator;
  readonly scanButton: Locator;
  readonly results: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("deduplication-page");
    this.collectionSelect = this.testId("dedup-collection-select");
    this.matchFields = this.testId("dedup-match-fields");
    this.scanButton = this.testId("dedup-scan-button");
    this.results = this.testId("dedup-results");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/dedup"));
    await this.waitForLoadingComplete();
  }

  /** Selects the first real collection option (index 0 is the placeholder). */
  async selectFirstCollection(): Promise<string> {
    const options = this.collectionSelect.locator("option");
    const value = await options.nth(1).getAttribute("value");
    await this.collectionSelect.selectOption(value ?? "");
    return value ?? "";
  }

  /** Clicks the first available match-field chip. */
  async pickFirstMatchField(): Promise<void> {
    await this.matchFields.locator("button").first().click();
  }

  async scan(): Promise<void> {
    await this.scanButton.click();
  }
}
