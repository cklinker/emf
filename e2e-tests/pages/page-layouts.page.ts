import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class PageLayoutsPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly editButton: Locator;
  readonly deleteButton: Locator;
  readonly editorCanvas: Locator;
  readonly toolbar: Locator;
  readonly fieldPalette: Locator;
  readonly propertyPanel: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId("add-layout-button");
    this.table = this.testId("page-layouts-table");
    this.editButton = this.page.locator('[data-testid^="edit-button-"]');
    this.deleteButton = this.page.locator('[data-testid^="delete-button-"]');
    this.editorCanvas = this.testId("layout-editor");
    this.toolbar = this.page.locator('[data-testid^="design-button-"]');
    this.fieldPalette = this.page.locator(".field-palette");
    this.propertyPanel = this.page.locator(".property-panel");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/layouts"));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }

  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="layout-row-"]').count();
  }
}
