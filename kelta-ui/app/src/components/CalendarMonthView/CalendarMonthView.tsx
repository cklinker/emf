/**
 * CalendarMonthView Component (app-data-entry slice 6)
 *
 * `viewType='calendar'` renderer for ObjectListPage: a dependency-free month
 * grid (CSS grid + Intl/native Date) bound to one date/datetime field. Records
 * appear as chips in their day cell (≤3, then "+N more"), click through to the
 * record. The page owns the month state and merges the visible-range gte/lte
 * filters into the standard records query.
 */

import React, { useMemo } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

import { addMonths, currentMonthKey } from './monthMath'

/** Max record chips per day cell before collapsing into "+N more". */
const MAX_CHIPS_PER_DAY = 3

export interface CalendarMonthViewProps {
  records: CollectionRecord[]
  /** Date/datetime field the records are placed by. */
  dateField: FieldDefinition
  /** Chip label field name. */
  titleField: string
  /** Visible month ('YYYY-MM'); owned by the page (it drives the range filter). */
  month: string
  onMonthChange: (month: string) => void
  onRecordClick: (record: CollectionRecord) => void
}

export function CalendarMonthView({
  records,
  dateField,
  titleField,
  month,
  onMonthChange,
  onRecordClick,
}: CalendarMonthViewProps): React.ReactElement {
  const [year, monthNum] = month.split('-').map(Number)

  // Bucket records by ISO date part (works for date and datetime values).
  const byDay = useMemo(() => {
    const map = new Map<string, CollectionRecord[]>()
    for (const record of records) {
      const raw = record[dateField.name]
      if (raw === null || raw === undefined || raw === '') continue
      const day = String(raw).slice(0, 10)
      if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) continue
      const bucket = map.get(day)
      if (bucket) bucket.push(record)
      else map.set(day, [record])
    }
    return map
  }, [records, dateField.name])

  const daysInMonth = new Date(year, monthNum, 0).getDate()
  // Weekday of the 1st (0 = Sunday) — leading blanks in the grid.
  const firstWeekday = new Date(year, monthNum - 1, 1).getDay()
  const localToday = new Date()
  const localTodayKey = `${localToday.getFullYear()}-${String(localToday.getMonth() + 1).padStart(2, '0')}-${String(localToday.getDate()).padStart(2, '0')}`

  const monthLabel = new Intl.DateTimeFormat(undefined, {
    month: 'long',
    year: 'numeric',
  }).format(new Date(year, monthNum - 1, 1))

  const weekdayLabels = useMemo(() => {
    const fmt = new Intl.DateTimeFormat(undefined, { weekday: 'short' })
    // 2023-01-01 was a Sunday.
    return Array.from({ length: 7 }, (_, i) => fmt.format(new Date(2023, 0, 1 + i)))
  }, [])

  const cells: Array<{ day: number; key: string } | null> = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => ({
      day: i + 1,
      key: `${month}-${String(i + 1).padStart(2, '0')}`,
    })),
  ]

  return (
    <div className="rounded-[10px] border border-border bg-card" data-testid="calendar-view">
      <div className="flex items-center justify-between border-b border-border px-3 py-2">
        <span className="text-sm font-semibold" data-testid="calendar-month-label">
          {monthLabel}
        </span>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={() => onMonthChange(addMonths(month, -1))}
            aria-label="Previous month"
            data-testid="calendar-prev"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-7"
            onClick={() => onMonthChange(currentMonthKey())}
            data-testid="calendar-today"
          >
            Today
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={() => onMonthChange(addMonths(month, 1))}
            aria-label="Next month"
            data-testid="calendar-next"
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
      <div className="grid grid-cols-7 border-b border-border">
        {weekdayLabels.map((label) => (
          <div
            key={label}
            className="px-2 py-1 text-center text-[11px] font-semibold uppercase tracking-[0.09em] text-muted-foreground"
          >
            {label}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {cells.map((cell, index) => {
          if (!cell) {
            return (
              <div
                key={`blank-${index}`}
                className="min-h-24 border-b border-r border-border/60 bg-muted/20"
              />
            )
          }
          const dayRecords = byDay.get(cell.key) ?? []
          const overflow = dayRecords.length - MAX_CHIPS_PER_DAY
          const isToday = cell.key === localTodayKey
          return (
            <div
              key={cell.key}
              className="min-h-24 border-b border-r border-border/60 p-1"
              data-testid={`calendar-day-${cell.key}`}
            >
              <div
                className={cn(
                  'mb-1 inline-flex h-5 w-5 items-center justify-center rounded-full text-xs tabular-nums',
                  isToday
                    ? 'bg-primary font-semibold text-primary-foreground'
                    : 'text-muted-foreground'
                )}
              >
                {cell.day}
              </div>
              <div className="space-y-0.5">
                {dayRecords.slice(0, MAX_CHIPS_PER_DAY).map((record) => {
                  const title = record[titleField]
                  return (
                    <button
                      key={record.id}
                      type="button"
                      className="block w-full truncate rounded bg-primary/10 px-1.5 py-0.5 text-left text-xs hover:bg-primary/20"
                      onClick={() => onRecordClick(record)}
                      data-testid={`calendar-chip-${record.id}`}
                    >
                      {title === null || title === undefined || title === ''
                        ? record.id
                        : String(title)}
                    </button>
                  )
                })}
                {overflow > 0 && (
                  <div
                    className="px-1.5 text-[11px] text-muted-foreground"
                    data-testid={`calendar-overflow-${cell.key}`}
                  >
                    +{overflow} more
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
