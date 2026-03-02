import type { Page, Locator } from "@playwright/test";
import { BasePage } from "./base.page";

export class CollectionWizardPage extends BasePage {
  readonly container: Locator;
  readonly stepIndicator: Locator;
  readonly navigation: Locator;
  readonly cancelButton: Locator;
  readonly backButton: Locator;
  readonly nextButton: Locator;
  readonly createButton: Locator;

  // Basics step
  readonly displayNameInput: Locator;
  readonly nameInput: Locator;
  readonly descriptionInput: Locator;
  readonly activeCheckbox: Locator;

  // Fields step
  readonly addFieldButton: Locator;
  readonly noFields: Locator;
  readonly fieldsTable: Locator;

  // Review step
  readonly reviewDisplayName: Locator;
  readonly reviewName: Locator;
  readonly reviewDescription: Locator;
  readonly reviewStatus: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.container = this.testId("collection-wizard-page");
    this.stepIndicator = this.testId("wizard-step-indicator");
    this.navigation = this.testId("wizard-navigation");
    this.cancelButton = this.testId("wizard-cancel-button");
    this.backButton = this.testId("wizard-back-button");
    this.nextButton = this.testId("wizard-next-button");
    this.createButton = this.testId("wizard-create-button");

    // Basics step
    this.displayNameInput = this.testId("wizard-display-name-input");
    this.nameInput = this.testId("wizard-name-input");
    this.descriptionInput = this.testId("wizard-description-input");
    this.activeCheckbox = this.testId("wizard-active-checkbox");

    // Fields step
    this.addFieldButton = this.testId("wizard-add-field-button");
    this.noFields = this.testId("wizard-no-fields");
    this.fieldsTable = this.testId("wizard-fields-table");

    // Review step
    this.reviewDisplayName = this.testId("wizard-review-display-name");
    this.reviewName = this.testId("wizard-review-name");
    this.reviewDescription = this.testId("wizard-review-description");
    this.reviewStatus = this.testId("wizard-review-status");
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl("/collections/new"));
    await this.waitForPageLoad();
  }

  async selectTemplate(template: string): Promise<void> {
    await this.testId(`wizard-template-${template}`).click();
  }

  async fillBasicInfo(info: {
    name?: string;
    displayName?: string;
    description?: string;
  }): Promise<void> {
    if (info.displayName) {
      await this.displayNameInput.fill(info.displayName);
    }
    if (info.name) {
      await this.nameInput.fill(info.name);
    }
    if (info.description) {
      await this.descriptionInput.fill(info.description);
    }
  }

  async addField(field: { name: string; type: string }): Promise<void> {
    await this.addFieldButton.click();
    // Field editor modal will appear; the exact interaction depends on
    // FieldEditor component internals. This provides the entry point.
  }

  async nextStep(): Promise<void> {
    await this.nextButton.click();
  }

  async previousStep(): Promise<void> {
    await this.backButton.click();
  }

  async submit(): Promise<void> {
    await this.createButton.click();
  }

  async getCurrentStep(): Promise<number> {
    const steps = this.page.locator('[data-testid^="wizard-step-circle-"]');
    const count = await steps.count();
    for (let i = 0; i < count; i++) {
      const step = steps.nth(i);
      const classes = await step.getAttribute("class");
      // The current step typically has a distinct style (e.g., filled/active)
      if (classes && /bg-primary|bg-blue/.test(classes)) {
        return i + 1;
      }
    }
    return 1;
  }

  stepCircle(stepNumber: number): Locator {
    return this.testId(`wizard-step-circle-${stepNumber}`);
  }

  fieldRow(fieldId: string): Locator {
    return this.testId(`wizard-field-row-${fieldId}`);
  }

  fieldEditButton(fieldId: string): Locator {
    return this.testId(`wizard-field-edit-${fieldId}`);
  }

  fieldRemoveButton(fieldId: string): Locator {
    return this.testId(`wizard-field-remove-${fieldId}`);
  }
}
