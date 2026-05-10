import { test, expect } from "../../fixtures";

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

    const fetched = await dataFactory.getRecord(parentName, parentRecord.id);
    expect(Number(fetched.attributes.totalAmount)).toBe(12);
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

    const fetched = await dataFactory.getRecord(parentName, parentRecord.id);
    expect(Number(fetched.attributes.lineCount)).toBe(3);
  });
});
