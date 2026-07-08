import type { WidgetPayload } from '../../../../hooks/useDashboardData'
import { useI18n } from '../../../../context/I18nContext'

export interface RecordsWidgetProps {
  /** `table` data {records, fields} + pagination, or `recent` data {records, totalCount}. */
  payload: WidgetPayload
  /** Row click → record drill-through (when the target collection is resolvable). */
  onRowClick?: (recordId: string) => void
}

/** Shared renderer for `table` and `recent` widgets: a compact record table. */
export function RecordsWidget({ payload, onRowClick }: RecordsWidgetProps) {
  const { t } = useI18n()
  const data = payload.data ?? {}
  const records = (data.records as Array<Record<string, unknown>>) ?? []
  const declared = (data.fields as string[] | null) ?? null

  if (records.length === 0) {
    return (
      <div className="text-sm text-muted-foreground" data-testid="records-empty">
        {t('analytics.noRecords', 'No records')}
      </div>
    )
  }

  const columns =
    declared && declared.length > 0
      ? declared
      : Object.keys(records[0])
          .filter((k) => k !== 'id' && !k.startsWith('_'))
          .slice(0, 6)

  return (
    <div className="overflow-x-auto" data-testid="records-widget">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            {columns.map((c) => (
              <th
                key={c}
                scope="col"
                className="px-2 py-1.5 text-left text-[11px] font-semibold uppercase tracking-[0.09em] text-foreground/80"
              >
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {records.map((row, i) => {
            const id = typeof row.id === 'string' ? row.id : null
            const clickable = !!(onRowClick && id)
            return (
              <tr
                key={id ?? i}
                className={`border-b border-border even:bg-muted/40 ${
                  clickable ? 'cursor-pointer hover:bg-primary/10' : ''
                }`}
                onClick={clickable ? () => onRowClick(id) : undefined}
                data-testid="records-row"
              >
                {columns.map((c) => {
                  const v = row[c]
                  const text =
                    v === null || v === undefined
                      ? '—'
                      : typeof v === 'object'
                        ? JSON.stringify(v)
                        : String(v)
                  return (
                    <td key={c} className="px-2 py-1.5 truncate max-w-56">
                      {text}
                    </td>
                  )
                })}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
