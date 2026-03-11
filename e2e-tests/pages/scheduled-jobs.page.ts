import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class ScheduledJobsPage extends BasePage {
  readonly createButton: Locator;
  readonly table: Locator;
  readonly formModal: Locator;
  readonly nameInput: Locator;
  readonly cronInput: Locator;
  readonly typeInput: Locator;
  readonly activeCheckbox: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.createButton = this.testId("add-scheduled-job-button");
    this.table = this.testId("scheduled-jobs-table");
    this.formModal = this.testId("scheduled-job-form-modal");
    this.nameInput = this.testId("scheduled-job-name-input");
    this.cronInput = this.testId("scheduled-job-cron-expression-input");
    this.typeInput = this.testId("scheduled-job-type-input");
    this.activeCheckbox = this.testId("scheduled-job-active-input");
    this.submitButton = this.testId("scheduled-job-form-submit");
    this.cancelButton = this.testId("scheduled-job-form-cancel");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/scheduled-jobs"));
    await this.waitForLoadingComplete();
  }

  async waitForTableLoaded(): Promise<void> {
    const tableOrEmpty = this.table.or(this.page.getByTestId("empty-state"));
    await this.waitForContentReady(tableOrEmpty);
  }

  async clickCreate(): Promise<void> {
    await this.createButton.click();
  }

  async fillForm({
    name,
    type,
    cronExpression,
    active,
  }: {
    name: string;
    type?: string;
    cronExpression?: string;
    active?: boolean;
  }): Promise<void> {
    await this.nameInput.fill(name);
    if (type !== undefined) {
      await this.typeInput.selectOption(type);
    }
    if (cronExpression !== undefined) {
      await this.cronInput.fill(cronExpression);
    }
    if (active !== undefined) {
      const isChecked = await this.activeCheckbox.isChecked();
      if (isChecked !== active) {
        await this.activeCheckbox.click();
      }
    }
  }

  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  async getRowCount(): Promise<number> {
    return this.page.locator('[data-testid^="scheduled-job-row-"]').count();
  }

  async clickEdit(index: number): Promise<void> {
    await this.testId(`edit-button-${index}`).click();
  }

  async clickDelete(index: number): Promise<void> {
    await this.testId(`delete-button-${index}`).click();
  }

  async clickLogs(index: number): Promise<void> {
    await this.testId(`logs-button-${index}`).click();
  }
}
