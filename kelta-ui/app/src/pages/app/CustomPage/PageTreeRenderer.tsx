/**
 * PageTreeRenderer
 *
 * Runtime renderer for a published page's component tree (`tree.components` of a page render
 * contract). Delegates to the shared registry-driven `RenderTree` — the same path the builder
 * preview uses — so built-in and plugin widgets render identically at runtime and design time.
 * Versioned via the render contract so the node schema can evolve.
 */
import React from 'react'
import { RenderTree } from '@/pages/PageBuilderPage/widgets/renderTree'
import type { BindingScope } from '@/pages/PageBuilderPage/model/bindingScope'
import {
  PageRuntimeProvider,
  type PageRuntimeValue,
} from '@/pages/PageBuilderPage/runtime/PageRuntimeContext'
import '@/pages/PageBuilderPage/widgets/builtins'

export interface PageNode {
  id: string
  type: string
  props?: Record<string, unknown>
  children?: PageNode[]
}

/**
 * Runtime renderer for a published page's component tree. Renders through the shared
 * `RenderTree` (registry-driven, also used by the builder preview).
 */
export function PageTreeRenderer({
  components,
  tenantSlug,
  scope,
  runtime,
}: {
  components: PageNode[]
  tenantSlug: string
  /** Live binding scope (slice 2d) threaded through the `RenderTree`. */
  scope?: BindingScope
  /** Page-level action deps (slice 2e: setVar/refreshData query key) for runtime event widgets. */
  runtime?: PageRuntimeValue
}): React.ReactElement {
  const runtimeValue: PageRuntimeValue = runtime ?? {
    tenantSlug,
    setVar: () => {},
    dataSourceQueryKey: (name: string) => ['page-data', name],
  }
  return (
    <PageRuntimeProvider value={runtimeValue}>
      <RenderTree components={components} tenantSlug={tenantSlug} mode="runtime" scope={scope} />
    </PageRuntimeProvider>
  )
}
