import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class PicklistsPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly descriptionInput: Locator;
  readonly sortedCheckbox: Locator;
  readonly restrictedCheckbox: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId("add-picklist-button");
    this.table = this.testId("picklists-table");
    this.formModal = this.testId("picklist-form-modal");
    this.nameInput = this.testId("picklist-name-input");
    this.descriptionInput = this.testId("picklist-description-input");
    this.sortedCheckbox = this.testId("picklist-sorted-input");
    this.restrictedCheckbox = this.testId("picklist-restricted-input");
    this.submitButton = this.testId("picklist-form-submit");
    this.cancelButton = this.testId("picklist-form-cancel");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/picklists"));
    await this.waitForLoadingComplete();
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm({
    name,
    description,
    sorted,
    restricted,
  }: {
    name: string;
    description?: string;
    sorted?: boolean;
    restricted?: boolean;
  }): Promise<void> {
    await this.nameInput.fill(name);
    if (description !== undefined) {
      await this.descriptionInput.fill(description);
    }
    if (sorted !== undefined) {
      const isChecked = await this.sortedCheckbox.isChecked();
      if (isChecked !== sorted) {
        await this.sortedCheckbox.click();
      }
    }
    if (restricted !== undefined) {
      const isChecked = await this.restrictedCheckbox.isChecked();
      if (isChecked !== restricted) {
        await this.restrictedCheckbox.click();
      }
    }
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="picklist-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }

  async closeForm(): Promise<void> {
    await this.testId("picklist-form-close").click();
  }
}
