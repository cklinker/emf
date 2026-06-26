/**
 * Metric (KPI) built-in. Displays a single live aggregate — the **total record count** of a bound
 * collection — instead of hardcoded text. It fetches one page (`page[size]=1`) over the authorized
 * JSON:API path (gateway + worker enforce Cerbos/FLS) and reads the count from the response metadata
 * (`PageResponse.totalElements` ← `meta.totalCount`); no rows are transferred.
 *
 * In editor mode it shows a sample number so the designer sees the shape without issuing a fetch.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Loader2, Sigma } from 'lucide-react'
import { useApi } from '@/context/ApiContext'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'
import { asString } from '../util'

type MetricFormat = 'number' | 'compact'

function readCollection(props: Record<string, unknown> | undefined): string | undefined {
  const dv = props?.dataView
  if (dv && typeof dv === 'object') {
    return asString((dv as Record<string, unknown>).collection) || undefined
  }
  return undefined
}

/** `number` → grouped (1,234); `compact` → short (1.2K). Locale-aware via Intl. */
function formatCount(n: number, format: MetricFormat): string {
  if (format === 'compact') {
    return new Intl.NumberFormat(undefined, {
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(n)
  }
  return n.toLocaleString()
}

function LiveCount({
  collection,
  format,
}: {
  collection: string
  format: MetricFormat
}): React.ReactElement {
  const { apiClient } = useApi()
  const { data, isLoading, isError } = useQuery({
    // One row only — we want the count from metadata, not the data.
    queryKey: ['page-metric', collection],
    queryFn: () => apiClient.getPage(`/api/${collection}?page[size]=1`),
    enabled: !!collection,
    staleTime: 60 * 1000,
    retry: false,
  })

  if (isLoading) {
    return (
      <Loader2
        className="h-6 w-6 animate-spin text-muted-foreground"
        data-testid="page-node-metric-loading"
      />
    )
  }
  if (isError) {
    return (
      <span className="text-destructive" data-testid="page-node-metric-error">
        —
      </span>
    )
  }
  return <>{formatCount(data?.totalElements ?? 0, format)}</>
}

function MetricRender({ node, mode }: WidgetRenderProps): React.ReactElement {
  const props = node.props ?? {}
  const collection = readCollection(props)
  const label = asString(props.label)
  const format: MetricFormat = props.format === 'compact' ? 'compact' : 'number'

  return (
    <div
      className="flex flex-col gap-1"
      data-testid="page-node-metric"
      data-collection={collection ?? ''}
    >
      <span className="text-3xl font-semibold text-foreground">
        {mode === 'editor' || !collection ? (
          <span data-testid="page-node-metric-sample">1,234</span>
        ) : (
          <LiveCount collection={collection} format={format} />
        )}
      </span>
      {label && <span className="text-sm text-muted-foreground">{label}</span>}
    </div>
  )
}

export const metricWidget: WidgetDescriptor = {
  type: 'metric',
  label: 'Metric',
  icon: Sigma,
  category: 'data',
  acceptsChildren: false,
  defaultProps: { dataView: {}, label: '', format: 'number' },
  propSchema: [
    // Reuses the shared data-source editor; only `dataView.collection` is read (the count is of the
    // whole collection — fields/limit, if set, are ignored).
    {
      key: 'dataView',
      label: 'Data source',
      kind: 'collection-picker',
      group: 'data',
      dependsOnCollection: true,
    },
    { key: 'label', label: 'Label', kind: 'text', bindable: true, group: 'content' },
    {
      key: 'format',
      label: 'Number format',
      kind: 'select',
      group: 'content',
      options: [
        { label: '1,234', value: 'number' },
        { label: '1.2K', value: 'compact' },
      ],
    },
  ],
  Render: MetricRender,
}
