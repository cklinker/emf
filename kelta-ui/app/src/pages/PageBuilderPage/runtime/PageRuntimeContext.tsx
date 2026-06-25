/**
 * Page-runtime context (slice 2e). Carries the page-level action dependencies a widget descriptor's
 * `Render` can't get from `WidgetRenderProps` alone — `setVar` (write a page variable), `refreshData`
 * (invalidate a named data-source query), `dataSourceQueryKey` (its React Query key), and `tenantSlug`.
 *
 * Provided by the runtime host (`CustomPage` via `PageTreeRenderer`). The editor preview does NOT provide
 * it — but the editor never mounts the runtime event-firing widgets (they render inert in `mode:'editor'`),
 * so the context is only ever read in runtime mode. {@link usePageRuntime} returns sensible no-ops when no
 * provider is present, so a descriptor that defensively reads it outside the runtime host never throws.
 */
import React, { createContext, useContext } from 'react'

export interface PageRuntimeValue {
  tenantSlug: string
  /** Write a page variable (2d `usePageVariables.setVar`). */
  setVar: (name: string, value: unknown) => void
  /** React Query key for a named data source — `refreshData` invalidates it. */
  dataSourceQueryKey: (name: string) => unknown[]
}

const NOOP_RUNTIME: PageRuntimeValue = {
  tenantSlug: '',
  setVar: () => {},
  dataSourceQueryKey: (name: string) => ['page-data', name],
}

const PageRuntimeContext = createContext<PageRuntimeValue | null>(null)

export function PageRuntimeProvider({
  value,
  children,
}: {
  value: PageRuntimeValue
  children: React.ReactNode
}): React.ReactElement {
  return <PageRuntimeContext.Provider value={value}>{children}</PageRuntimeContext.Provider>
}

/** Read the page-runtime deps. Returns no-ops when no provider is mounted (editor preview / tests). */
// eslint-disable-next-line react-refresh/only-export-components
export function usePageRuntime(): PageRuntimeValue {
  return useContext(PageRuntimeContext) ?? NOOP_RUNTIME
}
