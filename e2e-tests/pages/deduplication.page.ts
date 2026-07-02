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

  /** Option values in the collection picker, excluding the empty placeholder. */
  private async collectionValues(): Promise<string[]> {
    const values = await this.collectionSelect
      .locator("option")
      .evaluateAll((opts) =>
        opts
          .map((o) => (o as HTMLOptionElement).value)
          .filter((v) => v !== ""),
      );
    return values;
  }

  /** The first match-field chip (a button inside the match-fields container). */
  private firstFieldChip(): Locator {
    return this.matchFields.locator("button").first();
  }

  /**
   * Selects the first collection that exposes at least one matchable field, so the
   * detect flow can proceed. Returns the chosen collection name, or "" if none has fields.
   */
  async selectCollectionWithFields(): Promise<string> {
    for (const value of await this.collectionValues()) {
      await this.collectionSelect.selectOption(value);
      // Wait for the field area to settle (loading text clears), then check for a chip.
      const chip = this.firstFieldChip();
      if (await chip.isVisible({ timeout: 5_000 }).catch(() => false)) {
        return value;
      }
    }
    return "";
  }

  async pickFirstMatchField(): Promise<void> {
    await this.firstFieldChip().click();
  }

  async scan(): Promise<void> {
    await this.scanButton.click();
  }
}
