import { useI18n } from '../../../../context/I18nContext'

export interface MetricWidgetProps {
  data: Record<string, unknown>
}

/** Single-value KPI: {value, aggregateFunction, aggregateField, label, totalRecords}. */
export function MetricWidget({ data }: MetricWidgetProps) {
  const { formatNumber } = useI18n()
  const raw = data.value
  const value =
    typeof raw === 'number'
      ? formatNumber(raw)
      : raw === null || raw === undefined
        ? '—'
        : String(raw)
  const label = (data.label as string) || (data.aggregateFunction as string) || null
  return (
    <div className="flex h-full flex-col justify-center" data-testid="metric-widget">
      <div className="font-mono text-3xl font-bold tabular-nums">{value}</div>
      {label && (
        <div className="mt-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
          {label}
        </div>
      )}
    </div>
  )
}
