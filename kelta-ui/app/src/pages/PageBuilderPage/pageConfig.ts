/**
 * Page component-tree persistence helpers.
 *
 * The `ui-pages` collection only has a `config` JSON column — `layout`/`components` sent as
 * top-level attributes are dropped by the worker. So the builder must nest the tree inside
 * `config`. These pure helpers read/merge that shape (with back-compat for legacy top-level
 * `components`) and are unit-tested in isolation.
 */
import type { PageComponent, PageLayout, UIPage } from './PageBuilderPage'

export interface PageConfig {
  layout?: PageLayout
  components?: PageComponent[]
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
