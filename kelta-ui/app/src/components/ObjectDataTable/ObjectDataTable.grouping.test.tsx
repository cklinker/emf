import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ObjectDataTable } from './ObjectDataTable'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

const fields: FieldDefinition[] = [
  { id: 'f-name', name: 'name', displayName: 'Name', type: 'string', required: false },
  { id: 'f-status', name: 'status', displayName: 'Status', type: 'picklist', required: false },
  { id: 'f-amount', name: 'amount', displayName: 'Amount', type: 'currency', required: false },
]

// Pre-sorted by status, the shape the page delivers (group field prepended to the sort).
const records: CollectionRecord[] = [
  { id: 'r1', name: 'Acme', status: 'active', amount: 100 },
  { id: 'r2', name: 'Beta', status: 'active', amount: 50.5 },
  { id: 'r3', name: 'Gamma', status: 'closed', amount: 10 },
  { id: 'r4', name: 'Delta', status: null, amount: null },
]

function renderTable(props: Partial<React.ComponentProps<typeof ObjectDataTable>> = {}) {
  return render(
    <MemoryRouter initialEntries={['/acme/app/o/accounts']}>
      <ObjectDataTable
        records={records}
        fields={fields}
        onSortChange={vi.fn()}
        selectedIds={new Set()}
        onSelectionChange={vi.fn()}
        collectionName="accounts"
        groupBy="status"
        {...props}
      />
    </MemoryRouter>
  )
}

describe('ObjectDataTable grouping', () => {
  it('renders one header row per group, in arrival order, with counts', () => {
    renderTable()
    const active = screen.getByTestId('group-header-active')
    const closed = screen.getByTestId('group-header-closed')
    const empty = screen.getByTestId('group-header-')
    expect(within(active).getByText('(2)')).toBeInTheDocument()
    expect(within(closed).getByText('(1)')).toBeInTheDocument()
    expect(within(empty).getByText('(1)')).toBeInTheDocument()
    // Arrival order preserved
    const headers = screen.getAllByTestId(/^group-header-/)
    expect(headers.map((h) => h.getAttribute('data-testid'))).toEqual([
      'group-header-active',
      'group-header-closed',
      'group-header-',
    ])
    // All data rows still render
    for (const name of ['Acme', 'Beta', 'Gamma', 'Delta']) {
      expect(screen.getByText(name)).toBeInTheDocument()
    }
  })

  it('sums visible numeric columns per group and skips null values', () => {
    renderTable()
    expect(screen.getByTestId('group-sum-active-amount').textContent).toContain('150.5')
    expect(screen.getByTestId('group-sum-closed-amount').textContent).toContain('10')
    // Null-only group sums to 0 rather than NaN
    expect(screen.getByTestId('group-sum--amount').textContent).toContain('0')
    // Non-numeric columns get no sum chip
    expect(screen.queryByTestId('group-sum-active-name')).toBeNull()
  })

  it('labels the empty-value bucket with an em dash', () => {
    renderTable()
    expect(within(screen.getByTestId('group-header-')).getByText('—')).toBeInTheDocument()
  })

  it('resolves group labels through lookupDisplayMap', () => {
    renderTable({
      groupBy: 'status',
      lookupDisplayMap: { status: { active: 'Active Deals', closed: 'Closed Deals' } },
    })
    expect(
      within(screen.getByTestId('group-header-active')).getByText('Active Deals')
    ).toBeInTheDocument()
  })

  it('collapses and re-expands a group without touching the others', () => {
    renderTable()
    fireEvent.click(screen.getByTestId('group-toggle-active'))
    expect(screen.queryByText('Acme')).toBeNull()
    expect(screen.queryByText('Beta')).toBeNull()
    expect(screen.getByText('Gamma')).toBeInTheDocument()
    expect(screen.getByText('Delta')).toBeInTheDocument()
    // Header stays with its count
    expect(within(screen.getByTestId('group-header-active')).getByText('(2)')).toBeInTheDocument()
    fireEvent.click(screen.getByTestId('group-toggle-active'))
    expect(screen.getByText('Acme')).toBeInTheDocument()
  })

  it('select-all still spans collapsed rows', () => {
    const onSelectionChange = vi.fn()
    renderTable({ onSelectionChange })
    fireEvent.click(screen.getByTestId('group-toggle-active'))
    fireEvent.click(screen.getByLabelText('Select all rows'))
    const ids = onSelectionChange.mock.calls[0][0] as Set<string>
    expect(ids).toEqual(new Set(['r1', 'r2', 'r3', 'r4']))
  })

  it('renders flat with no group rows when groupBy is absent', () => {
    renderTable({ groupBy: undefined })
    expect(screen.queryAllByTestId(/^group-header-/)).toHaveLength(0)
    expect(screen.getByText('Acme')).toBeInTheDocument()
  })
})
