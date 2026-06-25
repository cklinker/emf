import { test, expect } from "../fixtures";
import { getApiToken } from "../fixtures/auth-tokens";

/**
 * Page Builder v2 — full authored-page behavior flow (slice 2e, the terminal behavior slice).
 *
 * This is the ONE owned end-to-end spec for the page-builder→OutSystems-parity effort
 * (parent spec §"End-to-end e2e ownership"; child spec 2e §6.5). It is the load-bearing
 * post-deploy guard: it asserts a REAL MUTATION (a record created by a button's onClick event
 * persists), not merely that the page renders.
 *
 * SKIPPED in CI / pre-deploy: the v2 builder admin route + the new event-authoring UI do not exist
 * until the feature is deployed (the same convention as `monitoring/system-health.spec.ts`). It is a
 * NAMED post-deploy deliverable — authored here, run against the deployed environment before the
 * feature is declared done. 1h later adds the negative authz case (a denied user → 404) to this suite.
 *
 * Flow under test:
 *   palette → drop a widget into a grid column → bind a prop → add a button with an onClick
 *   `createRecord` event → save → publish → open the runtime page → click the button →
 *   assert the created record persists (queried back via the JSON:API).
 */
test.describe.skip("Page Builder v2 — events & actions (post-deploy)", () => {
  test("a button onClick createRecord event persists a record on the runtime page", async ({
    page,
    dataFactory,
    tenantSlug,
    apiBaseUrl,
  }) => {
    // 1. Seed a target collection with a text field the button will write to.
    const collection = await dataFactory.createCollection();
    const collectionName = collection.attributes?.name as string;
    await dataFactory.addField(collection.id, {
      name: "status",
      displayName: "Status",
      type: "string",
    });

    const marker = `e2e-${Date.now()}`;

    // 2. Open the v2 page builder and create a new page.
    await page.goto(`/${tenantSlug}/setup/pages`);
    await page.getByRole("button", { name: /create page/i }).click();
    await page.getByLabel(/page name/i).fill(`E2E Events ${marker}`);
    await page.getByLabel(/path|slug/i).fill(`e2e-events-${marker}`);
    await page.getByRole("button", { name: /create|save/i }).click();

    // 3. Drop a grid, then a button into a grid column (palette → canvas DnD).
    await page
      .getByTestId("palette-item-grid")
      .dragTo(page.getByTestId("page-canvas-root"));
    await page
      .getByTestId("palette-item-button")
      .dragTo(page.getByTestId("page-node-column").first());

    // 4. Bind a prop (the button label) and author the onClick createRecord event.
    await page.getByTestId("page-node-button").first().click();
    await page.getByTestId("property-label").fill("Place order");

    // Add an onClick → createRecord action targeting our collection + a literal `status`.
    await page.getByTestId("event-add-onClick").click();
    await page
      .getByTestId("event-add-type-onClick")
      .selectOption("createRecord");
    await page.getByTestId("event-param-collection").fill(collectionName);
    await page.getByTestId("event-param-attr-add").click();
    await page.getByTestId("event-param-attr-key-0").fill("status");
    await page.getByTestId("event-param-attr-value-0").fill(marker);

    // 5. Save + publish.
    await page.getByRole("button", { name: /^save/i }).click();
    await page.getByRole("button", { name: /publish/i }).click();

    // 6. Open the published runtime page and click the button (fires the action).
    await page.goto(`/${tenantSlug}/app/p/e2e-events-${marker}`);
    await expect(page.getByTestId("page-node-button")).toBeVisible();
    await page.getByTestId("page-node-button").click();

    // 7. THE load-bearing assertion: the record was actually created (a real mutation), not just render.
    //    Query the collection back over the authorized JSON:API and confirm the marker row persists.
    const token = await getApiToken();
    await expect
      .poll(
        async () => {
          const url = `${apiBaseUrl}/${tenantSlug}/api/${collectionName}?filter[status][EQ]=${encodeURIComponent(marker)}`;
          const resp = await fetch(url, {
            method: "GET",
            headers: {
              "Content-Type": "application/vnd.api+json",
              Authorization: `Bearer ${token}`,
            },
          });
          if (!resp.ok) return 0;
          const body = (await resp.json()) as { data?: unknown[] };
          return (body.data ?? []).length;
        },
        {
          timeout: 15_000,
          message: "expected the onClick createRecord to persist a record",
        },
      )
      .toBeGreaterThan(0);
  });
});
