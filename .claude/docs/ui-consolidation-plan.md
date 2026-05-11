# Kelta UI Component Consolidation Plan

Status: proposal — supersedes ad-hoc growth of overlapping components in `kelta-ui/app/` and `kelta-web/packages/components/`.

This plan inventories the duplicate component families, proposes a unified API for each, lists the feature superset, names the migration strategy, orders the implementation work, and calls out the breaking-change risks (especially for the public `@kelta/components` plugin SDK).

---

## 1. Scope and constraints

**In scope.** App-level components under `kelta-ui/app/src/components/` and library components under `kelta-web/packages/components/src/`. The two surfaces have diverged: the admin/builder UI in `kelta-ui/app/` grew its own table/filter/form components against the live admin schema, while `kelta-web` grew a generic, plugin-facing variant driven by `@kelta/sdk` types and TanStack Query.

**Out of scope.** The shadcn primitives in `kelta-ui/app/src/components/ui/` are the base layer and stay as-is. Single-purpose admin forms (e.g. `CollectionForm`, `FieldEditor`, `ValidationRuleEditor`, `RecordTypeEditor`) are not duplicates of generic forms — they own their own domain and only share the underlying primitives.

**Constraint — public API.** `@kelta/components` is consumed by external plugins (see `kelta-web/packages/plugin-sdk` and `kelta-ui/app/src/services/componentRegistry.ts`). Any breaking change to its exported components requires a deprecation window. `kelta-ui/app/` is internal-only and can be refactored freely.

**Constraint — data-source asymmetry.** App-side components fetch via app-local hooks (`useCollectionRecords`, `useCollectionSchema`, `usePageLayout`); library-side components fetch via `KeltaClient.resource()` + TanStack Query. The unified components must support **both** modes (controlled and fetch-by-resource-name) without coupling the library variant to admin-only hooks.

---

## 2. Inventory

### 2.1 Data tables (highest priority — 3 components, ~1,241 LOC)

| Component | File | LOC | Consumers |
|---|---|---|---|
| `AdminDataTable` | `kelta-ui/app/src/components/AdminDataTable/AdminDataTable.tsx` | 306 | `PageLayoutsPage`, `ListViewsPage`, `FlowsPage`, `EmailTemplatesPage` |
| `ObjectDataTable` | `kelta-ui/app/src/components/ObjectDataTable/ObjectDataTable.tsx` | 513 | `pages/app/ObjectListPage` |
| `DataTable` | `kelta-web/packages/components/src/DataTable/DataTable.tsx` | 422 | Plugin SDK consumers (external); internal tests |

Shape differences:

- **AdminDataTable.** In-memory `rows: T[]`, `AdminColumn<T>[]` with `accessor`/`cell`/`sortable`/`hideable`, click-to-sort cycle (asc → desc → unset), `ColumnPicker` persisted to `localStorage` by `tableId`, inline `FilterBuilder` row using app-side `FilterExpression` (LayoutFilter shape), `renderActions(row)` slot, `onRowClick`.
- **ObjectDataTable.** `records: CollectionRecord[]` + `fields: FieldDefinition[]`, virtual scrolling above 100 rows via `@tanstack/react-virtual`, row selection w/ select-all + indeterminate, full keyboard navigation (`useTableKeyboardNav` — arrows, Home/End, Enter, Space, Escape), `FieldRenderer` per cell, lookup display map for ref fields, built-in Edit/Delete dropdown, route-aware row click via `react-router`.
- **DataTable.** `resourceName` drives fetch via `useKeltaClient` + `useQuery`, controlled or uncontrolled sort/filters, pagination UI built-in, `ColumnDefinition<T>` with `render(value, row)`, simple keyboard navigation, no virtualization, no column picker, no inline filter UI.

### 2.2 Filter builders (2 components, 486 LOC)

| Component | File | LOC | Consumers |
|---|---|---|---|
| App `FilterBuilder` | `kelta-ui/app/src/components/FilterBuilder/FilterBuilder.tsx` | 222 | `AdminDataTable`, `PageLayoutsPage/components/LayoutEditorList`, `AssignmentRulesEditor`, `RulesEditor` |
| Library `FilterBuilder` | `kelta-web/packages/components/src/FilterBuilder/FilterBuilder.tsx` | 264 | Plugin SDK consumers; not used inside `kelta-ui/app/` |

Shape differences:

- **App.** `FilterExpression = { logic: 'AND' \| 'OR', filters: FilterClause[] }` with a single AND/OR group, 11 operators including `is_null`/`is_not_null`, single text `Input` for all value types, shadcn `Select`, namespaced test ids via `idPrefix`.
- **Library.** `FilterExpression[]` from `@kelta/sdk` (flat — no AND/OR), `OPERATORS_BY_TYPE` map keyed by field type (string/number/boolean/date/datetime), value-input type-aware (`number`, `date`, `datetime-local`, `select` for boolean), `maxFilters` cap with counter, plain `<select>`/`<input>` (no shadcn).

### 2.3 Field rendering (2 components, ~1,044 LOC)

| Component | File | LOC | Consumers |
|---|---|---|---|
| `LayoutRenderer` | `kelta-web/packages/components/src/LayoutRenderer/LayoutRenderer.tsx` | 604 | Plugin SDK consumers; `kelta-ui/app/` uses `useLayoutRules`/`isVisible` exports but not the renderer itself |
| `FieldRenderer` | `kelta-ui/app/src/components/FieldRenderer/FieldRenderer.tsx` | 440 | `ObjectDataTable`, `RelatedList`, `DetailSection`, `pages/app/ObjectDetailPage`, `pages/app/ObjectFormPage` |

Shape differences:

- **LayoutRenderer.** Renders a full `PageLayout` (sections/columns/tabs/related-list slots), handles visibility-rule evaluation, has both `view` and `edit` mode renderers with `customRenderers?: Record<string, FieldRendererFn>` keyed by **field name**, integrates with the `RuleEngine` for compute/validate/default/transform rules. Default cell renderers are minimal (string-coerce).
- **FieldRenderer.** **View-mode only** but covers 21+ field types with rich UX: tooltip-truncated string/rich_text, locale-aware number/currency/percent formatting, relative-time tooltip for `datetime`, `mailto:`/`tel:`/external links with stop-propagation, Badge variants for picklist/multi_picklist, React Router `<Link>` for `reference`/`lookup`/`master_detail`, Hash/Calculator/MapPin/Lock icons, `Copy` re-export. Plugin override via `componentRegistry.getFieldRenderer(type)` keyed by **field type**, wrapped in `PluginErrorBoundary`.

### 2.4 Form components (~1,339 LOC across 4 components)

| Component | File | LOC | Role |
|---|---|---|---|
| `ResourceForm` | `kelta-web/packages/components/src/ResourceForm/ResourceForm.tsx` | 626 | Generic `FieldDefinition[]`-driven form, RHF + Zod, integrates with `KeltaClient` + plugin registry. Public API. |
| `ResourceFormPage` | `kelta-ui/app/src/pages/ResourceFormPage/ResourceFormPage.tsx` | (page) | Page-level wrapper around `LayoutFormSections`, app-local plugin integration, breadcrumbs |
| `ObjectFormPage` | `kelta-ui/app/src/pages/app/ObjectFormPage/ObjectFormPage.tsx` | (page) | App-side analogue of `ResourceFormPage`, uses `useCollectionSchema`/`useRecord` |
| `LayoutFormSections` | `kelta-ui/app/src/components/LayoutFormSections/LayoutFormSections.tsx` | 259 | Section/column orchestrator. Takes a `renderField` callback — no field rendering of its own. |
| `CollectionForm` | `kelta-ui/app/src/components/CollectionForm/CollectionForm.tsx` | 454 | **Not a duplicate.** Single-purpose form for the Collections admin entity (name/displayName/active/displayFieldId). Keep as-is. |

The genuinely-overlapping work is `ResourceForm` vs the `ResourceFormPage`/`ObjectFormPage` + `LayoutFormSections` triplet: both reach the same outcome (a layout-aware, schema-driven form with plugin field renderers), via different stacks. `LayoutFormSections` is reusable and not itself a duplicate — it's the missing piece in `ResourceForm`.

### 2.5 Related lists (2 components, 444 LOC)

| Component | File | LOC | Role |
|---|---|---|---|
| `RelatedList` | `kelta-ui/app/src/components/RelatedList/RelatedList.tsx` | 376 | Compact 5-row table of child records, "+ New" / "View All", `FieldRenderer`-aware cells, auto-discovery or explicit `displayColumns`, optional `includedData` (JSON:API includeds) to avoid extra fetches |
| `LayoutRelatedLists` | `kelta-ui/app/src/components/LayoutRelatedLists/LayoutRelatedLists.tsx` | 68 | Pure orchestrator: sorts `LayoutRelatedListDto[]` and instantiates `RelatedList` per entry |

`LayoutRelatedLists` is a composition, not a duplicate. It stays. The consolidation work here is making sure `RelatedList` uses the unified `DataTable` once that exists, instead of hand-rolling its own compact table.

---

## 3. Proposed unified components

The naming convention picks the **library** name (so the public surface is stable) and folds the app-side features into it. New components live in `kelta-web/packages/components/src/`; the app-side wrappers shrink to thin adapters over app-local hooks.

### 3.1 Unified `DataTable`

```ts
// kelta-web/packages/components/src/DataTable/DataTable.tsx
export interface DataTableProps<T> {
  // --- data source (mutually exclusive) ---
  /** Controlled rows. When provided, fetching is the caller's responsibility. */
  rows?: T[]
  /** Uncontrolled — fetch via KeltaClient.resource(resourceName). */
  resourceName?: string

  // --- columns ---
  columns: ColumnDef<T>[]
  rowKey: (row: T) => string

  // --- features ---
  sort?: SortState | null
  onSortChange?: (s: SortState | null) => void
  filter?: FilterExpression | null
  onFilterChange?: (f: FilterExpression | null) => void
  showFilterBar?: boolean

  pagination?: PaginationState | 'auto' | false
  onPageChange?: (page: number) => void

  selection?: { mode: 'single' | 'multi'; ids: Set<string>; onChange: (ids: Set<string>) => void }
  rowActions?: (row: T) => React.ReactNode
  onRowClick?: (row: T) => void

  // --- virtualization ---
  virtual?: { enabled: boolean; threshold?: number; estimatedRowHeight?: number; maxHeight?: number }

  // --- persistence ---
  tableId?: string  // namespaces ColumnPicker + saved view state to localStorage

  // --- behavior ---
  emptyState?: React.ReactNode
  loading?: boolean
  keyboardNavigation?: boolean  // default true; uses useTableKeyboardNav internally
  className?: string
  testId?: string
}

export interface ColumnDef<T> {
  id: string
  header: string
  accessor?: (row: T) => unknown
  cell?: (row: T, ctx: { isFocused: boolean }) => React.ReactNode
  sortable?: boolean
  hideable?: boolean
  width?: string
  headerClassName?: string
  cellClassName?: string
}
```

**Feature superset:**

| Feature | Admin | Object | Library | Unified |
|---|---|---|---|---|
| Column-config driven | ✓ | ✓ (fields) | ✓ | ✓ |
| Custom cell renderer | ✓ | via FieldRenderer | ✓ | ✓ |
| Click-to-sort (3-state) | ✓ | ✓ (2-state) | ✓ (3-state) | ✓ (3-state) |
| Column picker + persist | ✓ | — | — | ✓ |
| Inline filter row | ✓ | — (toolbar) | — | ✓ (opt-in) |
| Row selection + select-all | — | ✓ | ✓ | ✓ |
| Keyboard navigation | — | ✓ (full) | ✓ (basic) | ✓ (full) |
| Virtual scrolling | — | ✓ | — | ✓ (opt-in) |
| Pagination UI | — | (external) | ✓ | ✓ |
| Row actions slot | ✓ | ✓ (built-in menu) | — | ✓ |
| Loading/empty/error states | ✓ | ✓ | ✓ | ✓ |
| TanStack Query fetch | — | (external hook) | ✓ | ✓ (when `resourceName`) |
| CSV/JSON export | — | (page-level) | — | ✓ (via callback) |
| Saved column visibility | ✓ | — | — | ✓ |

### 3.2 Unified `FilterBuilder`

```ts
// kelta-web/packages/components/src/FilterBuilder/FilterBuilder.tsx
export interface FilterBuilderProps {
  value: FilterExpression | null
  onChange: (next: FilterExpression | null) => void
  fields: FieldOption[]   // { name, displayName, type? }
  /** Cap on clauses. Omit for unlimited. */
  maxClauses?: number
  /** Hide the AND/OR toggle and force a logic value. */
  forceLogic?: 'AND' | 'OR'
  /** Use shadcn primitives instead of plain selects. Default true. */
  useShadcnPrimitives?: boolean
  idPrefix?: string
  className?: string
  testId?: string
}

export interface FilterExpression {
  logic: 'AND' | 'OR'
  filters: FilterClause[]
}
export interface FilterClause {
  field: string
  op: FilterOperator           // unified set, see below
  value?: unknown
}
```

**Operator superset** (drop dual `eq`/`equals` aliasing — pick one canonical set):

`equals`, `not_equals`, `contains`, `starts_with`, `ends_with`, `gt`, `gte`, `lt`, `lte`, `is_null`, `is_not_null`, `in`, `not_in`

**Feature superset:**

| Feature | App | Library | Unified |
|---|---|---|---|
| AND/OR group | ✓ | — | ✓ (toggleable) |
| Field-type-aware operators | — | ✓ | ✓ |
| Field-type-aware value input | — | ✓ | ✓ |
| `is_null` / `is_not_null` | ✓ | — | ✓ |
| Max-clauses cap + counter | — | ✓ | ✓ |
| shadcn primitives | ✓ | — | ✓ (opt-out) |
| Test-id namespacing | ✓ | — | ✓ |
| `in` / `not_in` (multi-value) | — | — | ✓ (new) |

### 3.3 Unified `FieldRenderer`

Promote the app-side `FieldRenderer` into `@kelta/components`. The library default renderers used inside `LayoutRenderer` become a thin wrapper around it.

```ts
// kelta-web/packages/components/src/FieldRenderer/FieldRenderer.tsx
export interface FieldRendererProps {
  type: FieldType
  value: unknown
  mode?: 'view' | 'edit'           // default 'view'
  onChange?: (next: unknown) => void  // required when mode === 'edit'
  fieldName?: string
  displayName?: string
  truncate?: boolean

  // reference / lookup
  tenantSlug?: string
  targetCollection?: string
  displayLabel?: string

  // routing — plug in a router-agnostic Link
  Link?: React.ComponentType<{ to: string; className?: string; onClick?: React.MouseEventHandler; children: React.ReactNode }>

  // formatting overrides
  locale?: string
  numberFormat?: Intl.NumberFormatOptions
  dateFormat?: Intl.DateTimeFormatOptions

  className?: string
}
```

**Feature superset:**

| Feature | App | Library | Unified |
|---|---|---|---|
| 21+ field types | ✓ | partial (10) | ✓ |
| View mode | ✓ | ✓ | ✓ |
| Edit mode | — | ✓ (basic) | ✓ (basic + extensible) |
| Tooltip-truncate long strings | ✓ | — | ✓ |
| Relative-time tooltip on datetime | ✓ | — | ✓ |
| `mailto:` / `tel:` / external links | ✓ | — | ✓ |
| Badge for picklist / multi_picklist | ✓ | — | ✓ |
| Router `<Link>` for refs | ✓ (RR-bound) | — | ✓ (injected Link) |
| Plugin registry override | ✓ (by **type**) | ✓ (by **name**) | ✓ (both, type wins as fallback) |
| Error boundary around plugin renderer | ✓ | — | ✓ |
| Locale / format overrides | partial | — | ✓ |

Removing the React-Router coupling (via the injected `Link` prop) is what unblocks moving it into the library.

### 3.4 Unified `ResourceForm`

The app-side `LayoutFormSections` is the missing layout-aware shell that `ResourceForm` lacks. Bring it into the library and have `ResourceForm` consume it, then drop `kelta-ui/app`'s separate `LayoutFormSections` + page-level form wiring.

```ts
// kelta-web/packages/components/src/ResourceForm/ResourceForm.tsx
export interface ResourceFormProps {
  // --- data source ---
  resourceName?: string
  recordId?: string
  initialValues?: Record<string, unknown>

  // --- explicit-schema mode (skips useDiscovery) ---
  fields?: FieldDefinition[]
  layout?: PageLayout            // optional — when provided, render via LayoutFormSections

  // --- callbacks ---
  onSave: (data: unknown) => void
  onCancel: () => void

  // --- behavior ---
  readOnly?: boolean
  mode?: 'view' | 'edit'
  enableLayoutRules?: boolean    // turns on useLayoutRules / RuleEngine

  // --- customization ---
  fieldRenderer?: React.ComponentType<FieldRendererProps>  // override the default
  className?: string
}
```

**Feature superset:**

| Feature | Library RF | App pages | Unified |
|---|---|---|---|
| RHF + Zod | ✓ | partial | ✓ |
| Plugin field-renderer registry | ✓ | ✓ | ✓ |
| Layout-driven sections/columns | — | ✓ | ✓ (via `LayoutFormSections`) |
| Visibility rules | — | ✓ | ✓ |
| Compute / validate / default rules | — | ✓ (via `useLayoutRules`) | ✓ (opt-in) |
| Label / required / readOnly overrides on layout | — | ✓ | ✓ |
| `helpTextOverride` | — | ✓ | ✓ |
| Direct `fields[]` mode (no layout) | ✓ | — | ✓ |
| `resourceName` fetch mode | ✓ | — | ✓ |

### 3.5 Unified `RelatedList`

Keep `LayoutRelatedLists` as the orchestrator; rebase `RelatedList` on the unified `DataTable` with `virtual: { enabled: false }`, fixed `pagination: false`, and inject custom toolbar slots for "+ New" / "View All".

No new top-level API surface — `RelatedList` keeps its current props, but its internals collapse to a `<DataTable>` once §3.1 lands.

---

## 4. Migration strategy

For each family, the strategy is **fold app-side features into the library variant, then have the app import from `@kelta/components`**. Where the library variant is too thin, lift the app code into the library first, then deprecate the duplicate.

### 4.1 DataTable

1. Land the unified `DataTable` in `kelta-web/packages/components/src/DataTable/` keeping the existing `DataTableProps` signature **additive** (new fields optional, defaults preserve current library behavior).
2. Port `useTableKeyboardNav` and `ColumnPicker` from `kelta-ui/app` into `kelta-web/packages/components/src/DataTable/internal/` (verbatim move, no API change). Their app-side files become re-exports for the deprecation window.
3. Make `AdminDataTable` a thin shim: `export const AdminDataTable = DataTable` plus a deprecation comment. Page consumers update imports lazily.
4. Refactor `ObjectDataTable` to delegate to `DataTable` with `virtual.enabled = true`, custom `cell` renderers wrapping `FieldRenderer`. The route-aware `onRowClick` stays on the consumer (`ObjectListPage`), not in the table.
5. Once `pages/app/ObjectListPage` is on the unified `DataTable`, delete `kelta-ui/app/src/components/ObjectDataTable/`.
6. Delete `kelta-ui/app/src/components/AdminDataTable/` after all four consumers (PageLayouts/ListViews/Flows/EmailTemplates) are on `@kelta/components`.

**Base.** Library `DataTable`. It has the cleanest public API and is already plugin-exposed.
**Added.** Virtualization (from `ObjectDataTable`), full keyboard nav (from `ObjectDataTable`), column picker w/ persistence (from `AdminDataTable`), inline filter row (from `AdminDataTable`), row-actions slot (from both app variants).
**Removed.** The forced `kelta-datatable__*` CSS class names — replace with composable `className` + Tailwind classes used in `AdminDataTable`/`ObjectDataTable`. (Public ARIA roles preserved.)

### 4.2 FilterBuilder

1. Land the unified `FilterBuilder` in `kelta-web` with the new `FilterExpression` shape (`{ logic, filters[] }`). Add a one-shot migration helper `flatToExpression(flat: FilterExpression[]): FilterExpression` so existing library callers don't break on day one.
2. The app-side `FilterBuilder` becomes a re-export from `@kelta/components`.
3. Update plugin-SDK docs to point at the new shape; keep the flat-array API behind a `legacyFlat?: boolean` prop for one minor version.

**Base.** App-side. Its AND/OR + `is_null` model is a strict superset of the library variant's flat-array model.
**Added.** Field-type-aware operator lists, field-type-aware value inputs, `maxClauses` cap.

### 4.3 FieldRenderer

1. Lift `kelta-ui/app`'s `FieldRenderer` into `kelta-web/packages/components/src/FieldRenderer/`. Replace the React-Router `<Link>` import with the `Link` prop. Replace the `@/components/kelta` `EmptyValue` import with an inline span or a small `EmptyValue` library export.
2. Replace `LayoutRenderer`'s internal `defaultViewRenderers`/`renderEditField` with `<FieldRenderer mode={mode} ... />`.
3. App-side `FieldRenderer` becomes a re-export from `@kelta/components` that pre-wires `Link = react-router's Link` and `componentRegistry`.
4. Keep `componentRegistry` plugin lookup behavior. `LayoutRenderer`'s name-keyed `customRenderers` continues to win over type-keyed registry lookups (documented precedence).

**Base.** App-side `FieldRenderer`. Library's defaults are a strict subset.
**Added.** Edit-mode renderers (lifted from `LayoutRenderer.renderEditField`).
**Constraint.** The library cannot import `react-router-dom`. Route-aware behavior comes via the injected `Link` prop.

### 4.4 ResourceForm

1. Move `LayoutFormSections` into `kelta-web/packages/components/src/ResourceForm/LayoutFormSections.tsx`. Currently imports `isVisible` from `@kelta/components`, so the dep already flows the right direction.
2. Extend `ResourceForm` to accept an optional `layout: PageLayout` and `enableLayoutRules?: boolean`. When `layout` is provided, use `LayoutFormSections` internally and call `useLayoutRules` when enabled.
3. Migrate `kelta-ui/app/src/pages/ResourceFormPage` to call the unified `ResourceForm` with `layout` + `enableLayoutRules`. Delete app-local `LayoutFormSections` once the import is removed everywhere.
4. Migrate `pages/app/ObjectFormPage` similarly. Its lookup-display/picklist plumbing stays in the page (it's not generic form behavior).

**Base.** Library `ResourceForm`. RHF+Zod plumbing is sound.
**Added.** Layout-driven section/column rendering, visibility rules, layout-rule engine integration, label/required/readOnly/help-text overrides per placement.
**Untouched.** `CollectionForm` and other single-purpose admin forms (`FieldEditor`, `ValidationRuleEditor`, `RecordTypeEditor`, `PicklistValuesEditor`, `SecurityEditor`).

### 4.5 RelatedList

1. After §4.1, refactor `RelatedList` internals to render a `<DataTable>` with `virtual.enabled = false`, `pagination = false`, `selection` omitted. Wrap it in the existing Card shell.
2. `LayoutRelatedLists` is unchanged — it still maps over the layout DTO.

---

## 5. Dependency order

```
1. FieldRenderer    (no internal deps; unblocks ObjectDataTable cells)
2. FilterBuilder    (no internal deps; unblocks AdminDataTable filter row)
3. DataTable        (depends on FieldRenderer + FilterBuilder + ColumnPicker move)
4. RelatedList      (depends on DataTable)
5. ResourceForm     (depends on FieldRenderer; LayoutFormSections move is internal)
```

Rationale: `DataTable` is named "first priority" in the brief, but its full feature parity requires `FieldRenderer` (for ObjectDataTable cell parity) and `FilterBuilder` (for AdminDataTable inline-filter parity). Land those two first as independent PRs, then `DataTable` consumes them in PR #3. RelatedList rides on top. ResourceForm is independent of the table chain and can run in parallel after step 1.

Each step is a separate PR — keep them small (<800 LOC each). The autopilot loop enforces tests on every step, which catches regressions before stacking.

---

## 6. Risk assessment

### 6.1 Breaking-change risks

| Surface | Risk | Mitigation |
|---|---|---|
| `@kelta/components` exported `DataTable` | High — external plugins consume this | Keep current `DataTableProps` shape strictly additive. New features behind optional fields. CSS class names stay (or move to documented "legacy" mode). Deprecate, don't delete. |
| `@kelta/components` exported `FilterBuilder` | High — same as above | Provide `legacyFlat` prop + `flatToExpression()` migration helper. Cut over plugins in a follow-up minor release. |
| `@kelta/components` exported `LayoutRenderer` | Medium — depends on internal renderers becoming `FieldRenderer` | Default-renderer behavior must be byte-equal-or-better. Snapshot tests against current outputs before refactor. |
| `@kelta/components` exported `ResourceForm` | Medium — Zod schema generation may shift if rule engine wires in | Gate rule-engine integration behind `enableLayoutRules` (off by default). Existing callers see no change. |
| App-side imports `@/components/AdminDataTable` etc. | Low — internal | Keep app-side files as thin re-exports during deprecation; flip-the-switch PR removes them. |

### 6.2 Plugin-SDK impact

External plugins register field renderers via `componentRegistry.getFieldRenderer(type)` (app-side, keyed by field type) **and** via `LayoutRenderer.customRenderers[fieldName]` (library-side, keyed by field name). The unified `FieldRenderer` documents the precedence (name override > registry > built-in), preserving both registration paths. No plugin breakage is expected for view-mode renderers.

Plugin **edit-mode** renderers don't exist today on the app side — only on `LayoutRenderer`. Adding edit-mode to the unified `FieldRenderer` widens the surface but does not break existing view-only plugins.

### 6.3 Behavior parity risks

- **Sort cycle.** `AdminDataTable` and library `DataTable` both have 3-state (asc → desc → unset); `ObjectDataTable` is 2-state (asc → desc → asc). Unified component picks 3-state. `ObjectListPage` users may notice; document in the changelog.
- **Empty-cell rendering.** `AdminDataTable.renderDefaultCell` returns `null` for null; library returns `''`; `FieldRenderer` returns an `<EmptyValue>` span. Pick one — recommend `EmptyValue` for screen-reader cue parity. Visual change in the admin tables.
- **localStorage key for column picker.** `AdminDataTable` already namespaces by `tableId`. Preserve the existing key format (`kelta:adminDataTable:hiddenColumns:<tableId>` or whatever `loadHiddenColumns` reads) when the implementation moves into the library, so user-saved visibility survives the refactor.
- **Virtual scrolling threshold.** `ObjectDataTable` uses 100. Keep the default at 100 in the unified component; expose `virtual.threshold` for callers to tune.
- **CSS class names.** `kelta-datatable__*` and `kelta-filter-builder__*` BEM classes are part of the public theming surface for plugins. Keep them on a "legacy stylesheet" the consumer can opt into.

### 6.4 Test coverage risks

Existing tests cover each variant in isolation (`AdminDataTable.test.tsx`, `ObjectDataTable.DataTablePagination.test.tsx`, library `DataTable.test.tsx`, etc.). Before deleting any variant:

1. Migrate its tests onto the unified component with the equivalent props.
2. Keep the original test files until the variant file is deleted in the same PR.
3. Add E2E coverage in `e2e-tests/` for at least one page per family (list page → DataTable; layout-editor page → FilterBuilder; record form page → ResourceForm) so plugin regressions surface end-to-end.

### 6.5 Sequencing risks

Doing all five PRs in one branch would balloon past 4,000 LOC of moved code. Stack them as described in §5, each going through the autopilot CI gate. If PR #3 (`DataTable`) lands but a consumer regresses, the rollback is bounded to that PR — earlier `FieldRenderer`/`FilterBuilder` work stays in.

---

## 7. Open questions for the author of each follow-up PR

1. Does `KeltaClient.resource().list()` support sending the new `{ logic, filters[] }` `FilterExpression` shape, or does the worker `/api/...` endpoint still expect the flat array? (May need a small SDK adapter — separate task.)
2. Should `RelatedList`'s "+ New" / "View All" toolbar slots become public `DataTable` props (`headerActions`, `footerActions`), or stay as `RelatedList`-specific composition?
3. Do we want field-name-keyed plugin overrides on `FieldRenderer` directly (not just via `LayoutRenderer.customRenderers`)? Slight precedence-doc complication, but it would let admin tables apply field-specific overrides without going through a layout.
4. For `ResourceForm` with `enableLayoutRules: true`, who owns rule-violation surface — RHF errors map, or a separate `ruleViolations` callback? `LayoutRenderer` today exposes `RuleViolation[]` through `useLayoutRules` — preserve that signal.

These are not blockers for the plan itself; they're decisions to lock in at PR time.
