/**
 * Page component-tree persistence helpers.
 *
 * The `ui-pages` collection only has a `config` JSON column — `layout`/`components` sent as
 * top-level attributes are dropped by the worker. So the builder must nest the tree inside
 * `config`. These pure helpers read/merge that shape (with back-compat for legacy top-level
 * `components`) and are unit-tested in isolation.
 */
import type { PageComponent, PageLayout, UIPage } from './PageBuilderPage'

/**
 * A page-level state variable, referenced by bindings as `vars.<name>`. The `default`/value types
 * are widened here (`unknown`) and refined to the binding `PropValue` model in slices 2a/2d.
 */
export interface PageVariable {
  name: string
  type: 'string' | 'number' | 'boolean' | 'json'
  default?: unknown
  /**
   * Variable kind (app-platform slice 2). Absent = 'static' (today's behavior).
   * A 'computed' variable derives its value from `expression` over the live binding
   * scope; it has no stored state and rejects `setVar`.
   */
  kind?: 'static' | 'computed'
  /** Formula expression for `kind: 'computed'` (evaluated via the 2d expr path). */
  expression?: string
}

/**
 * An on-load data source — a declarative query the client runs over the authorized JSON:API path on
 * page load (never resolved server-side), exposed to bindings as `data.<name>`.
 */
export interface PageDataSource {
  name: string
  collection: string
  fields?: string[]
  filter?: unknown
  sort?: string[]
  limit?: number
  mode?: 'list' | 'single'
  recordId?: unknown
}

/**
 * The page's `config` JSON (the only persisted column). The component tree lives at `components`;
 * `variables`/`dataSources`/`access`/`schemaVersion` are siblings — there is no `tree` wrapper.
 * Persisting the new siblings on save is wired in slices 2c/2d; here they are type surface only.
 */
export interface PageConfig {
  /**
   * @deprecated Inert legacy (slice 2c). The widget tree + per-child `span` now own layout; the
   * create-form `layoutType` select still writes this and it round-trips untouched, but nothing in the
   * canvas/runtime reads it. Removal of the select is deferred to a follow-up.
   */
  layout?: PageLayout
  components?: PageComponent[]
  variables?: PageVariable[]
  dataSources?: PageDataSource[]
  access?: { requiredPermission?: string }
  /**
   * When true, this page overrides the end-user app's default landing page (`/app/home`) — the runtime
   * renders it in place of `AppHomePage`. At most one page per tenant should set this; the resolver
   * picks the first published + active page that does.
   */
  isHomePage?: boolean
  /** v2 marker — slice 2c is the first writer (stamped on every save once a tree is authored/migrated). */
  schemaVersion?: 2
}

type PageWithConfig = {
  config?: PageConfig
  components?: PageComponent[]
  layout?: PageLayout
}

/** Reads the component tree from a page, preferring `config.components` over legacy top-level. */
export function readComponents(page: Partial<UIPage> | null | undefined): PageComponent[] {
  if (!page) return []
  const p = page as PageWithConfig
  return p.config?.components ?? p.components ?? []
}

/** Reads the stored config object (`{ layout, components }`) from a page, or `{}`. */
export function readConfig(page: Partial<UIPage> | null | undefined): PageConfig {
  const p = (page ?? {}) as PageWithConfig
  return p.config ?? {}
}

/**
 * Merges new page-level config into an existing config, overlaying ONLY the keys explicitly passed and
 * preserving every untouched key. Widened in slice 2c to overlay the v2 page-level siblings
 * (`variables`/`dataSources`/`access`/`schemaVersion`) alongside `components`/`layout`.
 *
 * Load-bearing rule (parent §"Save & persistence"): a key is overlaid ONLY when the CALLER passes it.
 * Widening the accepted keys here is useless unless `handleSavePage` passes them — an omitted key is
 * never written (and never wiped). The save call site (`handleSavePage`) therefore passes the full set.
 */
export function mergeConfig(
  existing: PageConfig,
  changes: {
    components?: PageComponent[]
    layout?: PageLayout
    variables?: PageVariable[]
    dataSources?: PageDataSource[]
    access?: { requiredPermission?: string }
    isHomePage?: boolean
    schemaVersion?: 2
  }
): PageConfig {
  const merged: PageConfig = { ...existing }
  if (changes.layout !== undefined) merged.layout = changes.layout
  if (changes.components !== undefined) merged.components = changes.components
  if (changes.variables !== undefined) merged.variables = changes.variables
  if (changes.dataSources !== undefined) merged.dataSources = changes.dataSources
  if (changes.access !== undefined) merged.access = changes.access
  if (changes.isHomePage !== undefined) merged.isHomePage = changes.isHomePage
  if (changes.schemaVersion !== undefined) merged.schemaVersion = changes.schemaVersion
  return merged
}
