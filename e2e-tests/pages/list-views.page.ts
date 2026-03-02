import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class ListViewsPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formModal: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId("add-listview-button");
    this.table = this.testId("listviews-table");
    this.formModal = this.testId("listview-form-modal");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/listviews"));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm({
    name,
    collectionId,
    visibility,
  }: {
    name: string;
    collectionId?: string;
    visibility?: string;
  }): Promise<void> {
    await this.testId("listview-name-input").fill(name);
    if (collectionId !== undefined) {
      await this.testId("listview-collectionId-input").fill(collectionId);
    }
    if (visibility !== undefined) {
      await this.testId("listview-visibility-input").fill(visibility);
    }
  }

  async submitForm(): Promise<void> {
    await this.testId("listview-form-submit").click();
  }

  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="listview-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }
}
