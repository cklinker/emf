import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ObjectDataTable } from './ObjectDataTable'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'

const fields: FieldDefinition[] = [
  { id: 'f-name', name: 'name', displayName: 'Name', type: 'string', required: false },
]
const records: CollectionRecord[] = [{ id: 'r1', name: 'Acme' }]

function renderTable(props: Partial<React.ComponentProps<typeof ObjectDataTable>> = {}) {
  return render(
    <MemoryRouter>
      <ObjectDataTable
        records={records}
        fields={fields}
        onSortChange={vi.fn()}
        selectedIds={new Set()}
        onSelectionChange={vi.fn()}
        collectionName="accounts"
        {...props}
      />
    </MemoryRouter>
  )
}

describe('ObjectDataTable inline edit', () => {
  it('renders read-only cells by default (no inline affordance)', () => {
    renderTable()
    expect(screen.getByText('Acme')).toBeDefined()
    expect(screen.queryByTestId('inline-field-name')).toBeNull()
  })

  it('renders inline-editable cells when editable + onCellCommit are provided', () => {
    renderTable({ editable: true, onCellCommit: vi.fn().mockResolvedValue(undefined) })
    expect(screen.getByTestId('inline-field-name')).toBeDefined()
  })

  it('commits a cell edit with (recordId, fieldName, value)', async () => {
    const onCellCommit = vi.fn().mockResolvedValue(undefined)
    renderTable({ editable: true, onCellCommit })
    fireEvent.click(screen.getByTestId('inline-field-name'))
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'Globex' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    await waitFor(() => expect(onCellCommit).toHaveBeenCalledWith('r1', 'name', 'Globex'))
  })
})
