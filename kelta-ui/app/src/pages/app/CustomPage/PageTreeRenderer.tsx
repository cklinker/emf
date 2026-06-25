/**
 * PageTreeRenderer
 *
 * Schema-driven renderer for a builder component tree (the `tree.components` of a page render
 * contract). Maps the base node types produced by the page builder (heading, text, button, image,
 * card, container, table, form) to React elements, and falls back to the plugin
 * {@link componentRegistry} for any other node type. This is the runtime counterpart of the
 * builder's preview — versioned via the render contract so the node schema can evolve.
 */
import React, { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { componentRegistry } from '@/services/componentRegistry'
import { useApi } from '@/context/ApiContext'
import { RenderTree } from '@/pages/PageBuilderPage/widgets/renderTree'
import { RENDER_TREE_V2 } from '@/pages/PageBuilderPage/widgets/renderFlags'
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

/** A table node's data binding: which collection to read and which fields to show as columns. */
interface DataViewConfig {
  collection?: string
  fields?: string[]
  limit?: number
}

const MAX_TABLE_ROWS = 100

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function asStringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
}

function readDataView(props: Record<string, unknown>): DataViewConfig {
  const dv = props.dataView
  if (dv && typeof dv === 'object') {
    const o = dv as Record<string, unknown>
    return {
      collection: asString(o.collection) || undefined,
      fields: asStringList(o.fields),
      limit: typeof o.limit === 'number' ? o.limit : undefined,
    }
  }
  return {}
}

/**
 * Renders a bound data table. Fetches the collection through the normal JSON:API endpoint
 * (`apiClient.getList`), so the gateway + worker enforce Cerbos/FLS — denied fields are stripped
 * server-side; we only display the author-declared columns.
 */
function DataTableNode({ dataView }: { dataView: DataViewConfig }): React.ReactElement {
  const { apiClient } = useApi()
  const collection = dataView.collection
  const limit = Math.min(dataView.limit ?? 25, MAX_TABLE_ROWS)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['page-table', collection, limit],
    queryFn: () =>
      apiClient.getList<Record<string, unknown>>(`/api/${collection}?page[size]=${limit}`),
    enabled: !!collection,
    staleTime: 60 * 1000,
    retry: false,
  })

  if (!collection) {
    return (
      <div
        className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground"
        data-testid="page-node-table"
      >
        Table (no data source configured)
      </div>
    )
  }
  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-6" data-testid="page-node-table">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    )
  }
  if (isError) {
    return (
      <div
        className="rounded-md border border-border p-4 text-sm text-destructive"
        data-testid="page-node-table"
      >
        Could not load data.
      </div>
    )
  }

  const rows = data ?? []
  const columns =
    dataView.fields && dataView.fields.length > 0
      ? dataView.fields
      : rows.length > 0
        ? Object.keys(rows[0])
        : []

  return (
    <div className="overflow-x-auto rounded-md border border-border" data-testid="page-node-table">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="bg-muted">
            {columns.map((col) => (
              <th key={col} className="border-b border-border px-3 py-2 text-left font-medium">
                {col}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td
                className="px-3 py-4 text-center text-muted-foreground"
                colSpan={Math.max(columns.length, 1)}
              >
                No records
              </td>
            </tr>
          ) : (
            rows.map((row, i) => (
              <tr
                key={asString(row.id, String(i))}
                className="border-b border-border last:border-b-0"
              >
                {columns.map((col) => (
                  <td key={col} className="px-3 py-2">
                    {row[col] == null ? '' : String(row[col])}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}

/**
 * Renders a create form bound to a collection: one input per declared field, submitting a new
 * record through the authorized JSON:API path (`apiClient.postResource`) so Cerbos/FLS apply on
 * create. Field-level validation/types are a later refinement.
 */
function FormNode({ dataView }: { dataView: DataViewConfig }): React.ReactElement {
  const { apiClient } = useApi()
  const collection = dataView.collection
  const fields = dataView.fields ?? []
  const [values, setValues] = useState<Record<string, string>>({})

  const mutation = useMutation({
    mutationFn: () => apiClient.postResource(`/api/${collection}`, values),
    onSuccess: () => setValues({}),
  })

  if (!collection || fields.length === 0) {
    return (
      <div
        className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground"
        data-testid="page-node-form"
      >
        Form (no data source configured)
      </div>
    )
  }

  return (
    <form
      className="flex flex-col gap-3 rounded-md border border-border p-4"
      data-testid="page-node-form"
      onSubmit={(e) => {
        e.preventDefault()
        mutation.mutate()
      }}
    >
      {fields.map((field) => (
        <div key={field} className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground" htmlFor={`form-${field}`}>
            {field}
          </label>
          <input
            id={`form-${field}`}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={values[field] ?? ''}
            onChange={(e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))}
          />
        </div>
      ))}
      <div className="flex items-center gap-3">
        <button
          type="submit"
          className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
          disabled={mutation.isPending}
          data-testid="form-submit"
        >
          {mutation.isPending ? 'Submitting…' : 'Submit'}
        </button>
        {mutation.isSuccess && (
          <span className="text-sm text-primary" data-testid="form-success">
            Saved
          </span>
        )}
        {mutation.isError && (
          <span className="text-sm text-destructive" data-testid="form-error">
            Could not save
          </span>
        )}
      </div>
    </form>
  )
}

function renderChildren(node: PageNode, tenantSlug: string): React.ReactNode {
  return (node.children ?? []).map((child) => (
    <PageNodeRenderer key={child.id} node={child} tenantSlug={tenantSlug} />
  ))
}

function PageNodeRenderer({
  node,
  tenantSlug,
}: {
  node: PageNode
  tenantSlug: string
}): React.ReactElement {
  const props = node.props ?? {}

  switch (node.type) {
    case 'heading':
      return (
        <h2 className="text-2xl font-semibold text-foreground" data-testid="page-node-heading">
          {asString(props.text, 'Heading')}
        </h2>
      )
    case 'text':
      // The builder's text component stores its body in `content` (heading uses `text`).
      return (
        <p className="text-sm text-muted-foreground" data-testid="page-node-text">
          {asString(props.content, asString(props.text))}
        </p>
      )
    case 'button': {
      const label = asString(props.label, 'Button')
      const href = asString(props.href)
      const className =
        'inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground'
      return href ? (
        <a href={href} className={className} data-testid="page-node-button">
          {label}
        </a>
      ) : (
        <button type="button" className={className} data-testid="page-node-button">
          {label}
        </button>
      )
    }
    case 'image':
      return (
        <img
          src={asString(props.src)}
          alt={asString(props.alt, 'Image')}
          className="max-w-full rounded-md"
          data-testid="page-node-image"
        />
      )
    case 'card':
      return (
        <div className="rounded-lg border border-border bg-card p-4" data-testid="page-node-card">
          {renderChildren(node, tenantSlug)}
        </div>
      )
    case 'container':
      return (
        <div className="space-y-4" data-testid="page-node-container">
          {renderChildren(node, tenantSlug)}
        </div>
      )
    case 'table':
      return <DataTableNode dataView={readDataView(props)} />
    case 'form':
      return <FormNode dataView={readDataView(props)} />
    default: {
      const Comp = componentRegistry.getPageComponent(node.type)
      if (Comp) {
        return React.createElement(Comp, { config: props, tenantSlug })
      }
      return (
        <div className="text-xs text-muted-foreground" data-testid="page-node-unknown">
          Unknown component: {node.type}
        </div>
      )
    }
  }
}

/**
 * Legacy per-type switch renderer — retained as the fallback path while the v2 registry-based
 * `RenderTree` soaks (gated by {@link RENDER_TREE_V2}). Do not extend; new widgets register as
 * descriptors under `PageBuilderPage/widgets/builtins`.
 */
function LegacyPageTreeRenderer({
  components,
  tenantSlug,
}: {
  components: PageNode[]
  tenantSlug: string
}): React.ReactElement {
  if (!components || components.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground" data-testid="page-empty">
        This page has no content yet.
      </p>
    )
  }
  return (
    <div className="flex flex-col gap-4" data-testid="page-tree">
      {components.map((node) => (
        <PageNodeRenderer key={node.id} node={node} tenantSlug={tenantSlug} />
      ))}
    </div>
  )
}

/**
 * Runtime renderer for a published page's component tree. Delegates to the shared `RenderTree`
 * (registry-driven, also used by the builder preview) when {@link RENDER_TREE_V2} is on, else falls
 * back to the legacy switch renderer.
 */
export function PageTreeRenderer({
  components,
  tenantSlug,
  scope,
  runtime,
}: {
  components: PageNode[]
  tenantSlug: string
  /** Live binding scope (slice 2d). Only threaded through the v2 `RenderTree`; the legacy path ignores it. */
  scope?: BindingScope
  /** Page-level action deps (slice 2e: setVar/refreshData query key). Provided to runtime event widgets. */
  runtime?: PageRuntimeValue
}): React.ReactElement {
  if (RENDER_TREE_V2) {
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
  return <LegacyPageTreeRenderer components={components} tenantSlug={tenantSlug} />
}
