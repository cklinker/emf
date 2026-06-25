/**
 * Data built-in: table. Ported verbatim (behavior-preserving) from the runtime renderer's former
 * `DataTableNode`. Data still flows over the authorized JSON:API path so the gateway + worker enforce
 * Cerbos/FLS. In editor mode it renders a lightweight placeholder instead of issuing live fetches.
 *
 * The `form` widget moved to `./forms.tsx` in slice 2f, where it renders through `@kelta/components`'
 * typed/validated `ResourceForm` (replacing the old text-only `FormCreate`).
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Loader2, Grid3x3 } from 'lucide-react'
import { useApi } from '@/context/ApiContext'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'
import { asString, asStringList } from '../util'

const MAX_TABLE_ROWS = 100

interface DataViewConfig {
  collection?: string
  fields?: string[]
  limit?: number
}

function readDataView(props: Record<string, unknown> | undefined): DataViewConfig {
  const dv = props?.dataView
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

const DATA_SOURCE_FIELDS = [
  {
    key: 'dataView',
    label: 'Data source',
    kind: 'collection-picker' as const,
    group: 'data',
    dependsOnCollection: true,
  },
]

function DataTable({ dataView }: { dataView: DataViewConfig }): React.ReactElement {
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

function placeholder(icon: React.ReactNode, label: string, testid: string): React.ReactElement {
  return (
    <div
      className="flex flex-col items-center justify-center gap-2 rounded-md border border-dashed border-border bg-muted p-6 text-muted-foreground"
      data-testid={testid}
    >
      <span>{icon}</span>
      <span>{label}</span>
    </div>
  )
}

function TableRender({ node, mode }: WidgetRenderProps): React.ReactElement {
  if (mode === 'editor') return placeholder(<Grid3x3 size={24} />, 'Table', 'page-node-table')
  return <DataTable dataView={readDataView(node.props)} />
}

const table: WidgetDescriptor = {
  type: 'table',
  label: 'Table',
  icon: Grid3x3,
  category: 'data',
  defaultProps: {},
  propSchema: DATA_SOURCE_FIELDS,
  Render: TableRender,
}

export const dataWidgets: WidgetDescriptor[] = [table]
