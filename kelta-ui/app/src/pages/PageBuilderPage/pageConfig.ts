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
  layout?: PageLayout
  components?: PageComponent[]
  variables?: PageVariable[]
  dataSources?: PageDataSource[]
  access?: { requiredPermission?: string }
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

/** Merges new components/layout into an existing config, preserving untouched keys. */
export function mergeConfig(
  existing: PageConfig,
  changes: { components?: PageComponent[]; layout?: PageLayout }
): PageConfig {
  const merged: PageConfig = { ...existing }
  if (changes.layout !== undefined) merged.layout = changes.layout
  if (changes.components !== undefined) merged.components = changes.components
  return merged
}
