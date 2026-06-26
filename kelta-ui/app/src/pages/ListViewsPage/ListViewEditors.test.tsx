/**
 * Tests for the schema-driven List View editors (columns / filters / sort).
 */
import React, { useState } from 'react'
import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import {
  ColumnsEditor,
  FilterEditor,
  SortEditor,
  type EditorField,
  type FilterRow,
  type SortRow,
} from './ListViewEditors'

const FIELDS: EditorField[] = [
  { name: 'title', label: 'Title' },
  { name: 'year', label: 'Year' },
  { name: 'status', label: 'Status' },
]

describe('ColumnsEditor', () => {
  function Harness() {
    const [value, setValue] = useState<string[]>([])
    return (
      <>
        <ColumnsEditor fields={FIELDS} value={value} onChange={setValue} />
        <output data-testid="out">{value.join(',')}</output>
      </>
    )
  }

  it('prompts to pick a collection when there are no fields', () => {
    render(<ColumnsEditor fields={[]} value={[]} onChange={() => {}} />)
    expect(screen.getByText(/select a collection first/i)).toBeInTheDocument()
  })

  it('adds, reorders and removes columns', () => {
    render(<Harness />)
    fireEvent.click(screen.getByTestId('listview-add-column-title'))
    fireEvent.click(screen.getByTestId('listview-add-column-year'))
    expect(screen.getByTestId('out')).toHaveTextContent('title,year')

    // Move "year" up → year,title
    fireEvent.click(screen.getByLabelText('Move year up'))
    expect(screen.getByTestId('out')).toHaveTextContent('year,title')

    // Remove "year"
    fireEvent.click(screen.getByLabelText('Remove year'))
    expect(screen.getByTestId('out')).toHaveTextContent('title')
  })
})

describe('FilterEditor', () => {
  function Harness() {
    const [value, setValue] = useState<FilterRow[]>([])
    return (
      <>
        <FilterEditor fields={FIELDS} value={value} onChange={setValue} />
        <output data-testid="out">{JSON.stringify(value)}</output>
      </>
    )
  }

  it('adds a filter row defaulting to the first field + equals, and edits it', () => {
    render(<Harness />)
    fireEvent.click(screen.getByTestId('listview-add-filter'))
    expect(screen.getByTestId('out')).toHaveTextContent('[{"field":"title","op":"eq","value":""}]')

    fireEvent.change(screen.getByTestId('listview-filter-field-0'), { target: { value: 'status' } })
    fireEvent.change(screen.getByTestId('listview-filter-op-0'), { target: { value: 'contains' } })
    fireEvent.change(screen.getByTestId('listview-filter-value-0'), { target: { value: 'active' } })
    expect(screen.getByTestId('out')).toHaveTextContent(
      '[{"field":"status","op":"contains","value":"active"}]'
    )
  })

  it('hides the value input for a valueless operator (is empty)', () => {
    render(<Harness />)
    fireEvent.click(screen.getByTestId('listview-add-filter'))
    fireEvent.change(screen.getByTestId('listview-filter-op-0'), { target: { value: 'isnull' } })
    expect(screen.queryByTestId('listview-filter-value-0')).not.toBeInTheDocument()
  })
})

describe('SortEditor', () => {
  function Harness() {
    const [value, setValue] = useState<SortRow[]>([])
    return (
      <>
        <SortEditor fields={FIELDS} value={value} onChange={setValue} />
        <output data-testid="out">{JSON.stringify(value)}</output>
      </>
    )
  }

  it('adds sort rows, sets direction, and reorders', () => {
    render(<Harness />)
    fireEvent.click(screen.getByTestId('listview-add-sort'))
    fireEvent.change(screen.getByTestId('listview-sort-direction-0'), { target: { value: 'DESC' } })
    expect(screen.getByTestId('out')).toHaveTextContent('[{"field":"title","direction":"DESC"}]')

    fireEvent.click(screen.getByTestId('listview-add-sort'))
    fireEvent.change(screen.getByTestId('listview-sort-field-1'), { target: { value: 'year' } })
    // Move row 1 up → year first
    fireEvent.click(screen.getAllByLabelText('Move sort up')[1])
    expect(screen.getByTestId('out')).toHaveTextContent(
      '[{"field":"year","direction":"ASC"},{"field":"title","direction":"DESC"}]'
    )
  })
})
