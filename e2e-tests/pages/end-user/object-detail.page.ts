import type { Page, Locator } from "@playwright/test";
import { BasePage } from "../base.page";

export class ObjectDetailPage extends BasePage {
  readonly editButton: Locator;
  readonly deleteButton: Locator;
  readonly fieldValues: Locator;
  readonly relatedRecords: Locator;

  constructor(
    page: Page,
    private readonly collectionName: string,
    private readonly recordId: string,
    tenantSlug?: string,
  ) {
    super(page, tenantSlug);
    this.editButton = this.testId("edit-button");
    this.deleteButton = this.testId("delete-button");
    this.fieldValues = this.testId("field-values");
    this.relatedRecords = this.testId("related-records");
  }

  async goto(): Promise<void> {
    await this.page.goto(
      this.tenantUrl(`/app/o/${this.collectionName}/${this.recordId}`),
    );
    await this.waitForLoadingComplete();
  }

  async clickEdit(): Promise<void> {
    await this.editButton.click();
  }

  async clickDelete(): Promise<void> {
    await this.deleteButton.click();
  }

  async getFieldValue(name: string): Promise<string | null> {
    return this.testId(`field-value-${name}`).textContent();
  }
}
