import type { Page } from "@playwright/test";
import { test, expect } from "../../../fixtures";
import { getApiToken } from "../../../fixtures/auth-tokens";

const tenantSlug = process.env.E2E_TENANT_SLUG || "default";
const apiBaseUrl = process.env.E2E_API_BASE_URL || "https://kelta.io";

interface JsonApiResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

async function apiFetch(
  method: string,
  path: string,
  token: string,
  body?: unknown,
): Promise<JsonApiResource | null> {
  const url = `${apiBaseUrl}/${tenantSlug}${path}`;
  const response = await fetch(url, {
    method,
    headers: {
      "Content-Type": "application/vnd.api+json",
      Authorization: `Bearer ${token}`,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`${method} ${path} -> ${response.status}: ${text}`);
  }
  if (response.status === 204) return null;
  const json = (await response.json()) as { data: JsonApiResource };
  return json.data;
}

async function createPageLayout(
  token: string,
  collectionId: string,
  name: string,
): Promise<JsonApiResource> {
  const data = await apiFetch(
    "POST",
    `/api/page-layouts?collectionId=${encodeURIComponent(collectionId)}`,
    token,
    {
      data: {
        type: "page-layouts",
        attributes: {
          name,
          description: "e2e related-list column order",
          layoutType: "DETAIL",
          collectionId,
          isDefault: false,
        },
      },
    },
  );
  if (!data) throw new Error("page-layout create returned no data");
  return data;
}

async function deletePageLayout(token: string, id: string): Promise<void> {
  try {
    await apiFetch("DELETE", `/api/page-layouts/${id}`, token);
  } catch {
    // best-effort cleanup
  }
}

/**
 * Read the ordered field names currently rendered inside the
 * `rl-form-display-columns` list. Order is determined by DOM position.
 */
async function readDisplayColumnOrder(page: Page): Promise<string[]> {
  const list = page.getByTestId("rl-form-display-columns");
  await list.waitFor({ state: "visible" });
  const rows = list.getByRole("listitem");
  await rows.first().waitFor({ state: "visible" });
  const ids = await rows.evaluateAll((els: Element[]) =>
    els.map((el) => el.getAttribute("data-testid") || ""),
  );
  return ids.map((id: string) => id.replace(/^rl-form-column-/, ""));
}

/**
 * Move the focused row up or down by `delta` slots using Alt+Arrow keys.
 * Positive delta moves down, negative moves up.
 */
async function reorderFocusedRow(page: Page, delta: number): Promise<void> {
  const key = delta > 0 ? "Alt+ArrowDown" : "Alt+ArrowUp";
  for (let i = 0; i < Math.abs(delta); i++) {
    await page.keyboard.press(key);
  }
}

test.describe("Related List - Display Column Reordering", () => {
  let apiToken: string;
  let pageLayoutId: string | null = null;

  test.beforeAll(async () => {
    apiToken = await getApiToken();
  });

  test.afterEach(async () => {
    if (pageLayoutId) {
      await deletePageLayout(apiToken, pageLayoutId);
      pageLayoutId = null;
    }
  });

  test("reorders display columns and persists order across reopens", async ({
    page,
    dataFactory,
  }) => {
    // ---- Seed: parent + child collections, child has 3 displayable fields ----
    const parent = await dataFactory.createCollection({
      displayName: `RL Parent ${Date.now()}`,
    });
    const parentName = parent.attributes.name as string;

    const child = await dataFactory.createCollection({
      displayName: `RL Child ${Date.now()}`,
    });
    const childName = child.attributes.name as string;

    // Three displayable fields on the child, added in known order so we can
    // assert reordering vs the natural list.
    await dataFactory.addField(child.id, {
      name: "title",
      displayName: "Title",
      type: "string",
    });
    await dataFactory.addField(child.id, {
      name: "status",
      displayName: "Status",
      type: "string",
    });
    await dataFactory.addField(child.id, {
      name: "priority",
      displayName: "Priority",
      type: "string",
    });

    // Reference field on child pointing to parent — the relationship field
    // we'll wire the related list through.
    const parentRefField = await dataFactory.addField(child.id, {
      name: "parentRef",
      displayName: "Parent",
      type: "master_detail",
      referenceTarget: parentName,
    });

    await dataFactory.waitForStorageReady(parentName);
    await dataFactory.waitForStorageReady(childName);

    // ---- Create the page layout for the parent ----
    const layoutName = `rl-order-${Date.now()}`;
    const layout = await createPageLayout(apiToken, parent.id, layoutName);
    pageLayoutId = layout.id;

    // ---- Open the layout editor via the list page ----
    await page.goto(`/${tenantSlug}/layouts`);
    await page.getByTestId("page-layouts-table").waitFor({ state: "visible" });
    await page
      .getByRole("button", { name: `Design ${layoutName}` })
      .click();
    await page.getByTestId("layout-editor").waitFor({ state: "visible" });

    // ---- Open Add Related List dialog ----
    await page.getByTestId("add-related-list-button").click();
    const dialog = page.getByRole("dialog", { name: "Add Related List" });
    await dialog.waitFor({ state: "visible" });

    // Choose related collection (selects fire by collection id)
    await page
      .getByTestId("rl-form-collection")
      .selectOption({ value: child.id });

    // Pick the relationship field. The select uses field id as value, but
    // the loader fetches the child collection's fields async; wait until
    // the option is present.
    const relationshipSelect = page.getByTestId("rl-form-relationship");
    await expect(
      relationshipSelect.locator(`option[value="${parentRefField.id}"]`),
    ).toBeAttached({ timeout: 15_000 });
    await relationshipSelect.selectOption({ value: parentRefField.id });

    // Wait for the column rows to render. Natural order after the wait is
    // [title, status, priority] because of the order the fields were added.
    await expect(async () => {
      const order = await readDisplayColumnOrder(page);
      expect(order).toEqual(["title", "status", "priority"]);
    }).toPass({ timeout: 15_000 });

    // Check all 3 columns
    await page.getByTestId("rl-form-column-checkbox-title").check();
    await page.getByTestId("rl-form-column-checkbox-status").check();
    await page.getByTestId("rl-form-column-checkbox-priority").check();

    // ---- Reorder via keyboard: target order = [priority, title, status] ----
    // Move "priority" (index 2) up twice -> [priority, title, status]
    await page.getByTestId("rl-form-column-priority").focus();
    await reorderFocusedRow(page, -2);

    await expect(async () => {
      const order = await readDisplayColumnOrder(page);
      expect(order).toEqual(["priority", "title", "status"]);
    }).toPass({ timeout: 5_000 });

    // ---- Save the dialog, then save the layout ----
    await page.getByTestId("rl-form-submit").click();
    await dialog.waitFor({ state: "hidden" });

    await page.getByTestId("toolbar-save-button").click();

    // Wait for save toast (best-effort, don't block on it)
    await page
      .locator("[data-sonner-toast]")
      .first()
      .waitFor({ state: "visible", timeout: 10_000 })
      .catch(() => undefined);

    // ---- Reopen the related list and verify order survived persistence ----
    // Re-enter the editor cleanly to prove the order came from the server.
    await page.getByTestId("toolbar-back-button").click();
    await page.getByTestId("page-layouts-table").waitFor({ state: "visible" });
    await page.getByRole("button", { name: `Design ${layoutName}` }).click();
    await page.getByTestId("layout-editor").waitFor({ state: "visible" });

    // The pencil edit button's testid is `related-list-edit-<id>`; we don't
    // know the id, so target by role+aria-label.
    await page
      .getByRole("button", { name: "Edit related list" })
      .first()
      .click();

    const editDialog = page.getByRole("dialog", { name: "Edit Related List" });
    await editDialog.waitFor({ state: "visible" });

    await expect(async () => {
      const order = await readDisplayColumnOrder(page);
      // Selected items should be first in displayOrder per the helper logic.
      expect(order.slice(0, 3)).toEqual(["priority", "title", "status"]);
    }).toPass({ timeout: 15_000 });

    // All three checkboxes should still be checked.
    await expect(
      page.getByTestId("rl-form-column-checkbox-priority"),
    ).toBeChecked();
    await expect(
      page.getByTestId("rl-form-column-checkbox-title"),
    ).toBeChecked();
    await expect(
      page.getByTestId("rl-form-column-checkbox-status"),
    ).toBeChecked();
  });
});
