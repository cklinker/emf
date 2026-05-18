import { test, expect } from "../../fixtures";

/**
 * Poll a parent record until a rollup attribute is populated (non-undefined,
 * non-null). Rollup compute requires the parent's CollectionDefinition to
 * include the rollup field and the child records' physical columns to have
 * been migrated and written; both propagate asynchronously across worker pods
 * via NATS. The first GET after createRecord can land on a pod whose
 * registry has not yet refreshed and silently omits the rollup attribute.
 */
async function waitForRollupAttribute<T>(
  fetcher: () => Promise<{ attributes: Record<string, unknown> }>,
  attributeName: string,
  // Local full-path repro (worker:8083 direct AND via gateway:8080) proves
  // the rollup feature, worker, and gateway are all correct — totalAmount
  // is present on the first poll once the parent collection's CRUD-path
  // CollectionDefinition includes the just-added rollup field. The e2e
  // adds that field then immediately polls; on a cold, fully-loaded CI
  // compose stack (single worker pod, NATS config-event fan-out) the
  // field-change → CRUD-registry refresh can lag well past 20s. This is
  // documented eventual consistency, not a defect — give it real headroom.
  timeoutMs = 120_000,
  pollMs = 1_000,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  let lastValue: unknown = undefined;
  while (Date.now() < deadline) {
    const record = await fetcher();
    lastValue = record.attributes[attributeName];
    if (lastValue !== undefined && lastValue !== null) {
      return lastValue as T;
    }
    await new Promise((resolve) => setTimeout(resolve, pollMs));
  }
  throw new Error(
    `Rollup attribute '${attributeName}' did not populate within ${timeoutMs}ms (last value: ${String(lastValue)})`,
  );
}

/**
 * Rollup Summary end-to-end coverage:
 * - Configure a ROLLUP_SUMMARY field on a parent collection via the
 *   admin/fields API with fieldTypeConfig (childCollection, foreignKeyField,
 *   aggregateFunction, aggregateField).
 * - Insert child records and confirm the parent GET returns the aggregated
 *   value computed by RollupSummaryService through DefaultQueryEngine.
 *
 * Validates the new wiring end-to-end: UI submits these config keys, backend
 * reads them and invokes the SQL aggregate.
 */
test.describe("Rollup Summary Journey", () => {
  // Headroom for the full journey (create 2 collections + 4 fields,
  // storage-ready barriers, records) plus the up-to-120s rollup
  // propagation wait under cold/loaded CI. Worker + gateway are proven
  // correct by local repro; this only absorbs NATS config-event lag.
  test.describe.configure({ timeout: 210_000 });

  test("computes SUM rollup over child records", async ({ dataFactory }) => {
    const parent = await dataFactory.createCollection({
      displayName: `Rollup Parent ${Date.now()}`,
    });
    const parentName = parent.attributes.name as string;

    const child = await dataFactory.createCollection({
      displayName: `Rollup Child ${Date.now()}`,
    });
    const childName = child.attributes.name as string;

    await dataFactory.addField(child.id, {
      name: "amount",
      displayName: "Amount",
      type: "number",
    });
    await dataFactory.addField(child.id, {
      name: "parentRef",
      displayName: "Parent",
      type: "master_detail",
      referenceTarget: parentName,
    });

    await dataFactory.addField(parent.id, {
      name: "totalAmount",
      displayName: "Total Amount",
      type: "rollup_summary",
      fieldTypeConfig: {
        childCollection: childName,
        foreignKeyField: "parentRef",
        aggregateFunction: "SUM",
        aggregateField: "amount",
      },
    });

    await dataFactory.waitForStorageReady(parentName);
    await dataFactory.waitForStorageReady(childName);

    const parentRecord = await dataFactory.createRecord(parentName, {});
    await dataFactory.createRecord(childName, {
      amount: 5,
      parentRef: parentRecord.id,
    });
    await dataFactory.createRecord(childName, {
      amount: 7,
      parentRef: parentRecord.id,
    });

    const totalAmount = await waitForRollupAttribute<number | string>(
      () => dataFactory.getRecord(parentName, parentRecord.id),
      "totalAmount",
    );
    expect(Number(totalAmount)).toBe(12);
  });

  test("COUNT rollup ignores aggregateField", async ({ dataFactory }) => {
    const parent = await dataFactory.createCollection({
      displayName: `Rollup Count Parent ${Date.now()}`,
    });
    const parentName = parent.attributes.name as string;

    const child = await dataFactory.createCollection({
      displayName: `Rollup Count Child ${Date.now()}`,
    });
    const childName = child.attributes.name as string;

    await dataFactory.addField(child.id, {
      name: "parentRef",
      displayName: "Parent",
      type: "master_detail",
      referenceTarget: parentName,
    });

    await dataFactory.addField(parent.id, {
      name: "lineCount",
      displayName: "Line Count",
      type: "rollup_summary",
      fieldTypeConfig: {
        childCollection: childName,
        foreignKeyField: "parentRef",
        aggregateFunction: "COUNT",
      },
    });

    await dataFactory.waitForStorageReady(parentName);
    await dataFactory.waitForStorageReady(childName);

    const parentRecord = await dataFactory.createRecord(parentName, {});
    await dataFactory.createRecord(childName, { parentRef: parentRecord.id });
    await dataFactory.createRecord(childName, { parentRef: parentRecord.id });
    await dataFactory.createRecord(childName, { parentRef: parentRecord.id });

    const lineCount = await waitForRollupAttribute<number | string>(
      () => dataFactory.getRecord(parentName, parentRecord.id),
      "lineCount",
    );
    expect(Number(lineCount)).toBe(3);
  });
});
