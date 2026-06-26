/**
 * Chart built-in (slice 2g). A PURE presentational widget over `recharts` — it never fetches.
 *
 * `props.dataView` is a bindable data-source reference; by the time `Render` runs it is ALREADY resolved
 * (the resolved-node invariant — 2d's `renderNode` resolves bindings before `Render`) to the array result
 * of a page data source. `chart` consumes that array: `props.xKey` names the category/x field and each
 * `props.series[].key` names a numeric field on each row. The same `Render` is used by the editor preview
 * and the runtime; in editor mode (no live data) it shows a small sample so the designer sees a shape.
 */
/* eslint-disable react-refresh/only-export-components -- widget-descriptor module, not an HMR component file */
import React from 'react'
import { BarChart3 } from 'lucide-react'
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts'
import { useI18n } from '@/context/I18nContext'
import type { WidgetDescriptor, WidgetRenderProps } from '../types'

/** One plotted series, mapped to a numeric key in each row of the bound array. */
interface ChartSeries {
  key: string
  label?: string
  color?: string
}

const DEFAULT_COLORS = ['#3B82F6', '#06B6D4', '#8B5CF6', '#10B981', '#F59E0B', '#EF4444']

/** Editor placeholder so the designer sees a shape with no live data source. */
const EDITOR_SAMPLE: Array<Record<string, unknown>> = [
  { __x: 'A', total: 12, count: 4 },
  { __x: 'B', total: 19, count: 7 },
  { __x: 'C', total: 9, count: 3 },
  { __x: 'D', total: 22, count: 9 },
]

function asArray(v: unknown): Array<Record<string, unknown>> {
  return Array.isArray(v) ? (v as Array<Record<string, unknown>>) : []
}

function ChartRender({ node, mode }: WidgetRenderProps): React.ReactElement {
  const { t } = useI18n()
  const props = node.props ?? {}
  const chartType = typeof props.chartType === 'string' ? props.chartType : 'bar'
  const xKey =
    typeof props.xKey === 'string' && props.xKey ? props.xKey : mode === 'editor' ? '__x' : ''
  const series = Array.isArray(props.series) ? (props.series as unknown as ChartSeries[]) : []
  const height = typeof props.height === 'number' ? props.height : 300
  const showLegend = props.showLegend !== false
  const showGrid = props.showGrid !== false

  // `dataView` ({$bind:'data.orders'}) is resolved to an array BEFORE Render runs (2d). With none (or an
  // unresolved Binding object pre-2d), `asArray` returns [] → editor sample / runtime "No data".
  const resolved = asArray(props.dataView)
  const data = resolved.length > 0 ? resolved : mode === 'editor' ? EDITOR_SAMPLE : []
  const effectiveSeries: ChartSeries[] =
    series.length > 0
      ? series
      : mode === 'editor'
        ? [
            { key: 'total', label: 'Total', color: DEFAULT_COLORS[0] },
            { key: 'count', label: 'Count', color: DEFAULT_COLORS[1] },
          ]
        : []

  if (data.length === 0) {
    return (
      <div
        className="flex h-[var(--chart-h)] w-full items-center justify-center rounded-lg border border-dashed border-border text-sm text-muted-foreground"
        style={{ ['--chart-h' as string]: `${height}px` }}
        data-testid="page-node-chart-empty"
      >
        {t('builder.widget.chart.empty')}
      </div>
    )
  }

  return (
    <div className="w-full" data-testid="page-node-chart" data-chart-type={chartType}>
      <ResponsiveContainer width="100%" height={height}>
        {chartType === 'pie' ? (
          <PieChart>
            {showLegend && <Legend />}
            <Tooltip />
            <Pie
              data={data}
              dataKey={effectiveSeries[0]?.key ?? 'value'}
              nameKey={xKey}
              cx="50%"
              cy="50%"
              outerRadius="80%"
            >
              {data.map((_, i) => (
                <Cell key={i} fill={DEFAULT_COLORS[i % DEFAULT_COLORS.length]} />
              ))}
            </Pie>
          </PieChart>
        ) : chartType === 'line' ? (
          <LineChart data={data}>
            {showGrid && <CartesianGrid strokeDasharray="3 3" className="stroke-border" />}
            <XAxis dataKey={xKey} className="text-xs" />
            <YAxis className="text-xs" />
            <Tooltip />
            {showLegend && <Legend />}
            {effectiveSeries.map((s, i) => (
              <Line
                key={s.key}
                type="monotone"
                dataKey={s.key}
                name={s.label ?? s.key}
                stroke={s.color ?? DEFAULT_COLORS[i % DEFAULT_COLORS.length]}
                dot={false}
              />
            ))}
          </LineChart>
        ) : (
          <BarChart data={data}>
            {showGrid && <CartesianGrid strokeDasharray="3 3" className="stroke-border" />}
            <XAxis dataKey={xKey} className="text-xs" />
            <YAxis className="text-xs" />
            <Tooltip />
            {showLegend && <Legend />}
            {effectiveSeries.map((s, i) => (
              <Bar
                key={s.key}
                dataKey={s.key}
                name={s.label ?? s.key}
                fill={s.color ?? DEFAULT_COLORS[i % DEFAULT_COLORS.length]}
              />
            ))}
          </BarChart>
        )}
      </ResponsiveContainer>
    </div>
  )
}

export const chartWidget: WidgetDescriptor = {
  type: 'chart',
  label: 'Chart',
  icon: BarChart3,
  category: 'chart',
  acceptsChildren: false,
  defaultProps: {
    chartType: 'bar',
    xKey: '',
    series: [],
    height: 300,
    showLegend: true,
    showGrid: true,
  },
  propSchema: [
    // `dataView` is bindable to a page data source array, e.g. { $bind:'data.orders', mode:'path' }.
    { key: 'dataView', label: 'Data source', kind: 'expression', bindable: true, group: 'data' },
    {
      key: 'chartType',
      label: 'Chart type',
      kind: 'select',
      options: [
        { value: 'bar', label: 'Bar' },
        { value: 'line', label: 'Line' },
        { value: 'pie', label: 'Pie' },
      ],
      group: 'data',
    },
    { key: 'xKey', label: 'X axis key', kind: 'text', group: 'data' },
    // `series` is edited by a dedicated array editor the 2b inspector renders for kind:'expression'.
    { key: 'series', label: 'Series', kind: 'expression', group: 'data' },
    { key: 'height', label: 'Height (px)', kind: 'number', group: 'content' },
    { key: 'showLegend', label: 'Show legend', kind: 'boolean', group: 'content' },
    { key: 'showGrid', label: 'Show grid', kind: 'boolean', group: 'content' },
  ],
  Render: ChartRender,
}
