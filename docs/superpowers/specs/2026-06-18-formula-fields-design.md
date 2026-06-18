# Formula Fields — Design Spec

**Date:** 2026-06-18
**Status:** Approved

## Overview

A formula field is a read-only field whose value is automatically derived from other fields on the same record using an expression defined by an admin (e.g. `price * (1 + tax_rate)`). Users never type into it — the value is always computed. Formula fields are materialized to a real DB column so Superset can query them like any other field.

The formula language (parser, AST, evaluator, built-in functions) is already production-quality and in use for validation rules and workflow filters. This feature wires it to a new field type.

---

## Data Model

Formula field configuration lives in `fieldTypeConfig` alongside the existing field definition:

```json
{ "expression": "price * (1 + tax_rate)", "returnType": "NUMBER" }
```

**`returnType`** is one of `TEXT`, `NUMBER`, or `BOOLEAN`. It maps to the physical Postgres column type:

| returnType | Postgres column |
|---|---|
| TEXT | TEXT |
| NUMBER | NUMERIC |
| BOOLEAN | BOOLEAN |

The column is nullable. `returnType` is **immutable after field creation** — the admin must delete and recreate the field to change it. The UI disables the returnType selector on edit.

The `SchemaMigrationEngine` currently skips `FORMULA` fields (no physical column). It is updated to create a typed column, using `returnType` from `fieldTypeConfig`.

**Error representation:**
- `TEXT` formulas: store the literal string `"#ERROR"` in the column.
- `NUMBER` / `BOOLEAN` formulas: store `NULL` on error. The Kelta UI shows a red indicator for null values on formula fields (null is otherwise impossible for a computed field). Superset treats these as nulls.

The expression is validated on field save. An unparseable expression returns HTTP 400 with the parse error message.

---

## Backend Compute

### Write path — FormulaComputeHook

A new `FormulaComputeHook` in `kelta-worker/listener/` implements `BeforeSaveHook`.

On `beforeCreate` and `beforeUpdate`:
1. Find all `FORMULA` fields on the collection from the `CollectionDefinition`.
2. Topologically sort them by field references (formula A referencing formula B → B first). Circular references: all fields in the cycle get `"#ERROR"` / `null` and a `WARN` log.
3. For each formula field in order, evaluate the expression against the current record values using `FormulaEvaluator.evaluate(expression, record)`.
4. Inject the result via `BeforeSaveResult.withFieldUpdates`.
5. On `FormulaException` or any runtime error: inject `"#ERROR"` (TEXT) or `null` (NUMBER/BOOLEAN).

### Read path — DefaultQueryEngine

`DefaultQueryEngine.computeVirtualFields()` currently has a debug-log stub for `FORMULA` fields. It is updated to read `expression` from `fieldTypeConfig` and call `formulaEvaluator.evaluate(expression, record)`, falling back to the stored column value if `formulaEvaluator` is null.

This means **Kelta API consumers always receive a live computed value** regardless of what is stored. The DB column is the source of truth for Superset only.

---

## Background Recompute

Two triggers enqueue a bulk recompute job:

1. **Formula field created** — existing records have a NULL column until the job runs.
2. **Expression changed** — `FieldConfigEventPublisher` fires on every field update; the worker compares the incoming `fieldTypeConfig.expression` against the stored value and only enqueues a recompute if the expression actually changed (label/name updates are ignored).

The bulk job:
- Pages through all records in the collection in batches of 500.
- Evaluates the new expression for each batch and issues a bulk `UPDATE`.
- Runs entirely in the background — the field-create/update API call returns immediately.

While the job runs, Superset may see briefly stale values in the DB column. The Kelta UI is unaffected — it always computes live at read time.

**No priority queue needed.** Because the read path always computes live, Kelta UI users always see correct values during and after the recompute. The background job exists only to keep the DB column current for Superset.

---

## Admin UI

### Field creation/edit form

Selecting `FORMULA` as the field type reveals two additional inputs below the standard field name/label fields:

1. **Return type selector** — dropdown: Text / Number / Boolean. Required. Disabled (read-only) when editing an existing formula field.
2. **Expression editor** — a plain `<textarea>` paired with the existing `FieldExpressionPicker` component (already used in validation rules, approvals, and flows). The picker lists all other fields on the collection; clicking one inserts the field name at the cursor position. Users can also type field names directly.

Inline below the expression editor:
- **Validation error** — if the backend rejects the expression (unparseable), the error message is shown inline.
- **Live preview** — when the expression is syntactically valid and the collection has at least one record, the UI fetches the first record and shows the computed value next to the editor as a sanity check. Hidden if the collection is empty.

### Display in record views

Formula field values are displayed as read-only in the record detail page and list view. A red indicator (e.g. a small warning icon) is shown when the value is `null` (error state) on a NUMBER or BOOLEAN formula field.

---

## Testing

- **Unit:** `FormulaComputeHookTest` — covers field ordering (topological sort), circular references, error cases (`FormulaException`, null inputs), TEXT/#ERROR vs NUMBER/null error behavior.
- **Unit:** `SchemaMigrationEngineTest` — covers FORMULA column creation for each returnType.
- **Integration:** End-to-end test creates a collection with a formula field, creates a record, verifies the computed value is returned by the API and written to the DB column.
- **Integration:** Expression change triggers background recompute; DB column is updated.
- Existing `FormulaEvaluatorTest` and `FormulaEvaluatorParityTest` are unchanged.
