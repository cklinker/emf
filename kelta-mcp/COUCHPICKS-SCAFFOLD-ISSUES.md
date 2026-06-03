# Kelta MCP Issues & Wishlist

Running log of bugs, friction points, and missing MCP capabilities discovered while scaffolding the CouchPicks backend (`freestream` tenant) via the `kelta-admin` + `kelta-user` MCP servers exposed by this module. Updated live during the scaffold pass on 2026-06-02.

Format: `[severity] short title` — details, repro/example, suggested fix.

Legend: 🔴 blocker · 🟠 high · 🟡 medium · 🟢 nice-to-have · ✅ resolved

---

## Bugs

- 🔴 **Validation rules not enforced on record create/update.** Created rule `title_year_range` on `titles` with `errorConditionFormula: "year < 1888 OR year > 2031"`, `active: true`, `evaluateOn: CREATE_AND_UPDATE`, `severity: ERROR`. POST `/api/titles` with `year: 1500` succeeded (HTTP 201) — rule did not block. Either: formula parser silently failed (stored raw, not compiled), evaluator not wired into create path, or operator syntax is non-standard. Document supported formula language (Salesforce-style? SQL? JS?). Validation rule is the schema's #1 data-integrity primitive — must work.
- 🟠 **`DELETE /api/fields/{id}` returns 204 but underlying record column persists.** After dropping a field (`remove_field` MCP / direct REST DELETE), subsequent record reads/writes on the collection still expose the column in `data.attributes` (as null). Reproduced on `providers` — deleted `test_INTEGER`, `test_DOUBLE`, `test_LONG`, `ownerUser`, `ownerUser2`, `ownerUser3`, `test_JSON`. Field list now shows 13 (clean), but creating a Tubi record returns those names back in the response payload. Either (a) cascade-drop the Postgres column on field delete, or (b) project responses against the live field set.
- 🟡 **`create_record` response strips relationships block.** Response includes attributes but not the relationships top-level object (no `references`/`include` either). Hard to follow up with a related-record fetch. Include relationships in create response.
- 🟠 **`pageSize` / `pageNumber` query params ignored on `/api/collections`.** Pagination requires JSON:API bracket syntax `page[number]=N` and `page[size]=N`. But `/api/picklist-values` accepts flat `pageSize=N`. And `mcp__kelta-user__query_collection` MCP tool documents `pageNumber` and `pageSize` (flat) which works through that tool. Three inconsistent conventions. Pick one across REST + MCP.
- 🟠 **`/api/picklist-values?filter[picklistSourceId][EQ]=...&pageSize=1` returns totalCount but `data` is the first item only.** Useful but quirky — combine with explicit `data: []` selection helper. Document: "use pageSize=1 + read meta.totalCount for cheap count".
- 🔴 **`mcp__kelta-admin__create_picklist` returns HTTP 404.** Tool likely POSTs to wrong path. Direct REST `POST /api/global-picklists` succeeds (HTTP 201) with same auth. Tool description says "POST /api/picklistValues" for values but doesn't name the picklist endpoint. Fix: point tool at `/api/global-picklists` for picklist creation and `/api/picklist-values` for values. Also: `picklist-values` payload needs `attributes.picklistSourceType="GLOBAL"` + `attributes.picklistSourceId=<picklistUuid>` + `attributes.isActive` (not `active`) — not a JSON:API relationship.
- 🔴 **`mcp__kelta-admin__add_field` returns HTTP 400 for every field type.** Tested `text`, `picklist`. Native API requires uppercase types: `STRING`, `INTEGER` / `DOUBLE` / `LONG`, `BOOLEAN`, `DATE`, `DATETIME`, `PICKLIST`, `MULTI_PICKLIST`, `LOOKUP`, `REFERENCE`, `JSON`. MCP tool's enum (text/longText/number/integer/decimal/boolean/date/datetime/reference/picklist/multiPicklist/json/file/image) doesn't translate. Notably: `NUMBER` is rejected by native — must use `INTEGER`, `DOUBLE`, or `LONG`. Fix: map MCP type aliases to native uppercase + handle field-type-specific payload shape (PICKLIST needs `fieldTypeConfig.picklistSourceType/Id`, LOOKUP needs `relationships.referenceCollectionId`).
- 🔴 **Field name `type` rejected (HTTP 400).** Reserved JSON:API attribute name. Schema doc uses `Title.type`, `Alert.type` as PICKLIST fields — had to rename to `titleType` / `alertType`. Either (a) document the reserved-word list, (b) auto-mangle on storage and present unchanged on response, or (c) accept and namespace.
- 🟡 **404 errors return empty `errors[{}]` array.** Gateway 404 + 400 responses: `{"errors":[{}]}` (sometimes `[{},{}]`). No status, title, code, or detail per JSON:API spec. Cannot distinguish "wrong path" from "wrong payload" from "auth fail" from "validation rejection". Populate error objects with at minimum `status`, `code`, `detail`.
- 🟡 **`create_collection.displayFieldName` accepted but `displayName` stays null on response.** Set `displayFieldName: "name"` on providers → response shows `displayFieldName: "name"` (ok) but `displayName: null`. Either tool description is misleading or attribute mapping incomplete. Verify intent — likely two separate concepts (display field for records vs human label for collection itself).
- 🟡 **`add_field` MCP tool missing `indexed` and `searchable` flags** that exist on native `/api/fields` payload. Both are first-class field properties for query performance. Add them.
- 🟡 **`add_field` MCP tool missing `displayName` param.** Native API distinguishes machine `name` from human `displayName`. MCP only takes `fieldName`. Add `displayName`.
- 🟢 **No native field type for `longText` / `richText`.** Workaround: `STRING` with high `constraints.maxLength` (e.g. 100000). Loses semantic distinction in admin UI. Add `TEXT` and `RICH_TEXT` native types.

## Missing MCP tools

- 🟠 **No `delete_collection` tool on `kelta-admin`.** Cannot remove stale collections via MCP. Stale test artifact `e2e_wizard_1780349727631` (id `3363462a-8e1c-4f5d-96a7-498cc8be0c01`) had to be deleted via direct REST DELETE. Add `mcp__kelta-admin__delete_collection({ id })` wrapping `DELETE /api/collections/{id}`.
- 🟢 **No `list_picklists` / `get_picklist` tool.** Cannot inspect existing picklists or values via MCP — only create. Add read tools to support idempotent setup.
- 🟢 **No `delete_picklist` / `delete_validation_rule` tool.** Same as above for cleanup paths.
- 🟢 **No `add_picklist_value` / `deactivate_picklist_value` tool.** `create_picklist` accepts initial values atomically; no path to mutate later without REST.

## API/UX friction

- 🟠 **`add_field` type enum missing `vector`.** Schema doc requires `Title.embedding Vector(1536)` for recommendation similarity. Current `type` enum: text, longText, number, integer, decimal, boolean, date, datetime, reference, picklist, multiPicklist, json, file, image. Add `vector` with dimension param.
- 🟠 **`add_field` type enum missing `richText`.** Schema doc uses `RichText` for `Title.synopsis`, `EditorialList.description`. Currently substituting `STRING(maxLength=10000)` — loses formatting metadata. Add `richText`.
- 🟠 **No composite unique constraints.** `Availability(title, provider, region)`, `Watchlist(user, title)`, `EditorialListItem(list, position)`, `EditorialListItem(list, title)`, `Season(show, seasonNumber)`, `Episode(season, episodeNumber)` all require multi-column unique. `add_field.unique` is single-column only. Add `create_unique_constraint({ collectionName, fieldNames[] })` or accept `compositeUnique` array on `create_collection`.
- 🟡 **`add_field.defaultValue` is string-only.** Booleans / numbers must be stringified ("true", "360"). Source of subtle bugs. Accept native types.
- 🟢 **`create_collection.fields` returns "per-field success/failure" but no schema documented for that response.** Hard to handle partial-failure paths programmatically. Document response shape.

## Schema doc inconsistencies (CouchPicks `kelta-schema.md`)

- 🟡 **`Language` picklist not defined** but referenced by `Title.languages`, `Availability.audioLanguages`, `Availability.subtitleLanguages` (MultiPicklist ISO-639-1). Need explicit picklist definition. Created `Language` global picklist seeded with en/es/fr/de/it/pt/ja/ko/zh/ar/hi/ru.
- 🟢 **Inline picklists for `Show.status` and `ProviderSnapshot.status`** — promoted to global (`ShowStatus`, `SnapshotStatus`) for consistency with other status picklists. Update schema doc.
- 🟢 **Schema doc still defines custom `User` collection** in §7 but `project.md` §12 says use platform User. Schema doc §"Implementation order" still references it. Resolve by removing User from schema doc §7 and replacing with "Custom fields on platform User" section.
- 🟢 **`EditorialList.tags` MultiPicklist** — no underlying picklist named. Created `EditorialTag` picklist seeded with best-of / weekly-pick / leaving-soon / hidden-gem / classic / foreign / indie.

---

## Scaffold artifacts

Scripts live in `/tmp/` (not committed):
- `/tmp/scaffold.py` — idempotent collection + field creator via REST. 14 collections + platform User extension. Re-runnable.
- `/tmp/validation-rules.py` — 7 validation rules.
- `/tmp/couchpicks-picklists.json` — 19 picklist definitions (declarative source).
- `/tmp/create-picklists.sh` + `/tmp/create-picklist-values.sh` — REST-based picklist + value creator.

Promote to `kelta-mcp/scripts/` or `couchpicks/scripts/` once stabilized.

---

## Resolved

_(empty)_
