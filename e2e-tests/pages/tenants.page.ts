import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class TenantsPage extends BasePage {
  readonly tenantsPage: Locator;
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formOverlay: Locator;
  readonly formModal: Locator;
  readonly slugInput: Locator;
  readonly nameInput: Locator;
  readonly editionSelect: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.tenantsPage = this.testId("tenants-page");
    this.createButton = this.testId("create-tenant-button");
    this.table = this.testId("tenants-table");
    this.formOverlay = this.testId("tenant-form-overlay");
    this.formModal = this.testId("tenant-form-modal");
    this.slugInput = this.testId("tenant-slug-input");
    this.nameInput = this.testId("tenant-name-input");
    this.editionSelect = this.testId("tenant-edition-select");
    this.submitButton = this.testId("tenant-form-submit");
    this.cancelButton = this.testId("tenant-form-cancel");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/tenants"));
    await this.waitForLoadingComplete();
  }

  async waitForTableLoaded(): Promise<void> {
    const tableOrEmpty = this.table.or(this.page.getByTestId("empty-state"));
    await this.waitForContentReady(tableOrEmpty);
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm(data: {
    slug?: string;
    name?: string;
    edition?: string;
  }): Promise<void> {
    if (data.slug) await this.slugInput.fill(data.slug);
    if (data.name) await this.nameInput.fill(data.name);
    if (data.edition) {
      await this.editionSelect.click();
      await this.page.getByRole("option", { name: data.edition }).click();
    }
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.table.locator('[data-testid^="tenant-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`tenant-row-${index}`)
      .getByRole("button", { name: /edit/i })
      .click();
  }

  async clickSuspend(index: number): Promise<void> {
    await this.testId(`tenant-row-${index}`)
      .getByRole("button", { name: /suspend/i })
      .click();
  }
}
