import type { Page, Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class MenuBuilderPage extends BasePage {
  readonly menuBuilderPage: Locator;
  readonly menuList: Locator;
  readonly treeEditor: Locator;
  readonly addItemButton: Locator;
  readonly saveButton: Locator;

  constructor(page: Page, tenantSlug?: string) {
    super(page, tenantSlug);
    this.menuBuilderPage = this.testId('menu-builder-page');
    this.menuList = this.testId('menu-builder-menu-list');
    this.treeEditor = this.testId('menu-builder-tree-editor');
    this.addItemButton = this.testId('menu-builder-add-item-button');
    this.saveButton = this.testId('menu-builder-save-button');
  }

  async goto(): Promise<void> {
    await this.page.goto(this.tenantUrl('/menus'));
    await this.waitForLoadingComplete();
  }

  async getMenuCount(): Promise<number> {
    return this.menuList.locator('[data-testid^="menu-item-"]').count();
  }

  async clickMenu(index: number): Promise<void> {
    await this.menuList
      .locator('[data-testid^="menu-item-"]')
      .nth(index)
      .click();
  }

  async addItem(): Promise<void> {
    await this.addItemButton.click();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }
}
