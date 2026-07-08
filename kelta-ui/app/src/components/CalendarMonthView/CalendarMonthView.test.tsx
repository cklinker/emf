import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { CalendarMonthView } from './CalendarMonthView'
import { addMonths, monthRange, currentMonthKey } from './monthMath'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

const dateField: FieldDefinition = {
  id: 'f-due',
  name: 'dueDate',
  displayName: 'Due date',
  type: 'date',
  required: false,
}

describe('month helpers', () => {
  it('addMonths shifts within and across years', () => {
    expect(addMonths('2026-07', 1)).toBe('2026-08')
    expect(addMonths('2026-12', 1)).toBe('2027-01')
    expect(addMonths('2026-01', -1)).toBe('2025-12')
    expect(addMonths('2026-07', -19)).toBe('2024-12')
  })

  it('monthRange returns inclusive first/last day, leap-year aware', () => {
    expect(monthRange('2026-07')).toEqual({ gte: '2026-07-01', lte: '2026-07-31' })
    expect(monthRange('2026-02')).toEqual({ gte: '2026-02-01', lte: '2026-02-28' })
    expect(monthRange('2024-02')).toEqual({ gte: '2024-02-01', lte: '2024-02-29' })
  })

  it('currentMonthKey is YYYY-MM', () => {
    expect(currentMonthKey()).toMatch(/^\d{4}-\d{2}$/)
  })
})

const records: CollectionRecord[] = [
  { id: 'r1', name: 'Kickoff', dueDate: '2026-07-03' },
  { id: 'r2', name: 'Review', dueDate: '2026-07-03T14:30:00Z' },
  { id: 'r3', name: 'Ship', dueDate: '2026-07-21' },
  { id: 'r4', name: 'A', dueDate: '2026-07-10' },
  { id: 'r5', name: 'B', dueDate: '2026-07-10' },
  { id: 'r6', name: 'C', dueDate: '2026-07-10' },
  { id: 'r7', name: 'D', dueDate: '2026-07-10' },
  { id: 'r8', name: 'No date', dueDate: null },
]

function renderCalendar(props: Partial<React.ComponentProps<typeof CalendarMonthView>> = {}) {
  return render(
    <CalendarMonthView
      records={records}
      dateField={dateField}
      titleField="name"
      month="2026-07"
      onMonthChange={vi.fn()}
      onRecordClick={vi.fn()}
      {...props}
    />
  )
}

describe('CalendarMonthView', () => {
  it('shows the month label and buckets records by ISO date part', () => {
    renderCalendar()
    expect(screen.getByTestId('calendar-month-label').textContent).toContain('2026')
    const day3 = screen.getByTestId('calendar-day-2026-07-03')
    expect(within(day3).getByText('Kickoff')).toBeInTheDocument()
    // Datetime value buckets by its date part
    expect(within(day3).getByText('Review')).toBeInTheDocument()
    expect(
      within(screen.getByTestId('calendar-day-2026-07-21')).getByText('Ship')
    ).toBeInTheDocument()
  })

  it('collapses beyond three chips into +N more', () => {
    renderCalendar()
    const day10 = screen.getByTestId('calendar-day-2026-07-10')
    expect(within(day10).getAllByRole('button')).toHaveLength(3)
    expect(screen.getByTestId('calendar-overflow-2026-07-10').textContent).toBe('+1 more')
  })

  it('pages months and jumps to today', () => {
    const onMonthChange = vi.fn()
    renderCalendar({ onMonthChange })
    fireEvent.click(screen.getByTestId('calendar-prev'))
    expect(onMonthChange).toHaveBeenCalledWith('2026-06')
    fireEvent.click(screen.getByTestId('calendar-next'))
    expect(onMonthChange).toHaveBeenCalledWith('2026-08')
    fireEvent.click(screen.getByTestId('calendar-today'))
    expect(onMonthChange).toHaveBeenCalledWith(currentMonthKey())
  })

  it('fires onRecordClick from a chip', () => {
    const onRecordClick = vi.fn()
    renderCalendar({ onRecordClick })
    fireEvent.click(screen.getByTestId('calendar-chip-r3'))
    expect(onRecordClick).toHaveBeenCalledWith(records[2])
  })
})
