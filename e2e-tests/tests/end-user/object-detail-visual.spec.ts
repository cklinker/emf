/**
 * Visual-regression coverage for the runtime record detail page.
 *
 * Seeds a deterministic collection + record, navigates to the detail view,
 * then takes a full-page screenshot in both dark and light themes. The
 * theme is forced by setting `kelta_theme_mode` in localStorage before the
 * page loads so the snapshot doesn't depend on the user's system preference.
 *
 * Dynamic content (record id, timestamps, generated initials) is masked so
 * snapshots don't churn on every run.
 *
 * To generate / refresh baselines locally:
 *   pnpm playwright test object-detail-visual.spec.ts --update-snapshots
 *
 * In CI, baselines are checked in alongside the spec; missing baselines
 * cause the test to fail rather than silently generating noise.
 */

import { test, expect } from "../../fixtures";
import { ObjectDetailPage } from "../../pages/end-user/object-detail.page";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";

const MASK_SELECTORS = [
  // Record id pill in RecordHeader (UUID changes per run)
  '[data-component="RecordHeader"] .font-mono',
  // System-information rail rows (created / updated timestamps + author)
  '[data-component="MetadataCard"] dd',
  // System-information tab card (timestamps + ids)
  '[data-testid="system-internal-id"]',
  '[data-testid="system-created-at"]',
  '[data-testid="system-updated-at"]',
];

test.describe("Record detail — visual regression", () => {
  test.describe.configure({ mode: "serial" });

  test("dark theme", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
      required: true,
    });
    await dataFactory.addField(collection.id, {
      name: "summary",
      displayName: "Summary",
      type: "string",
    });
    await dataFactory.waitForStorageReady(
      collection.attributes.name as string,
    );

    const record = await dataFactory.createRecord(
      collection.attributes.name as string,
      {
        title: "Visual snapshot record",
        summary: "Stable seed for visual regression",
      },
    );

    // Force dark theme before navigation.
    await page.addInitScript(() => {
      window.localStorage.setItem("kelta_theme_mode", "dark");
    });

    const detail = new ObjectDetailPage(
      page,
      collection.attributes.name as string,
      record.id as string,
      tenantSlug,
    );
    await detail.goto();

    await expect(detail.fieldValues).toBeVisible();

    await expect(page).toHaveScreenshot("object-detail-dark.png", {
      fullPage: true,
      mask: MASK_SELECTORS.map((s) => page.locator(s)),
      // 0.5% tolerance for sub-pixel font rendering across machines
      maxDiffPixelRatio: 0.005,
      animations: "disabled",
    });
  });

  test("light theme", async ({ page, dataFactory }) => {
    const collection = await dataFactory.createCollection();
    await dataFactory.addField(collection.id, {
      name: "title",
      displayName: "Title",
      type: "string",
      required: true,
    });
    await dataFactory.waitForStorageReady(
      collection.attributes.name as string,
    );

    const record = await dataFactory.createRecord(
      collection.attributes.name as string,
      { title: "Visual snapshot record" },
    );

    await page.addInitScript(() => {
      window.localStorage.setItem("kelta_theme_mode", "light");
    });

    const detail = new ObjectDetailPage(
      page,
      collection.attributes.name as string,
      record.id as string,
      tenantSlug,
    );
    await detail.goto();

    await expect(detail.fieldValues).toBeVisible();

    await expect(page).toHaveScreenshot("object-detail-light.png", {
      fullPage: true,
      mask: MASK_SELECTORS.map((s) => page.locator(s)),
      maxDiffPixelRatio: 0.005,
      animations: "disabled",
    });
  });
});
