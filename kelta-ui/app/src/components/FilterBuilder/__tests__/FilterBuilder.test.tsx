import React from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { FilterBuilder, type FilterExpression } from '../FilterBuilder'

const fields = [
  { name: 'status', displayName: 'Status' },
  { name: 'industry', displayName: 'Industry' },
]

function Harness({
  initial,
  onChange,
}: {
  initial: FilterExpression | null
  onChange: (next: FilterExpression | null) => void
}) {
  const [value, setValue] = React.useState<FilterExpression | null>(initial)
  return (
    <FilterBuilder
      value={value}
      onChange={(next) => {
        setValue(next)
        onChange(next)
      }}
      fields={fields}
    />
  )
}

describe('FilterBuilder', () => {
  it('renders only the Add button when value is null', () => {
    render(<Harness initial={null} onChange={vi.fn()} />)
    expect(screen.getByTestId('filter-add')).toBeInTheDocument()
    expect(screen.queryByTestId('filter-clause-0')).toBeNull()
  })

  it('emits a new clause when Add is clicked', async () => {
    const onChange = vi.fn()
    render(<Harness initial={null} onChange={onChange} />)
    await userEvent.click(screen.getByTestId('filter-add'))
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        logic: 'AND',
        filters: [expect.objectContaining({ field: 'status', op: 'equals', value: '' })],
      })
    )
  })

  it('hides the value input for is_null / is_not_null operators', () => {
    render(
      <Harness
        initial={{ logic: 'AND', filters: [{ field: 'status', op: 'is_null' }] }}
        onChange={vi.fn()}
      />
    )
    const clause = screen.getByTestId('filter-clause-0')
    expect(within(clause).queryByTestId('filter-value-0')).toBeNull()
  })

  it('hides logic selector with a single clause', () => {
    render(
      <Harness
        initial={{ logic: 'AND', filters: [{ field: 'status', op: 'equals', value: 'a' }] }}
        onChange={vi.fn()}
      />
    )
    expect(screen.queryByTestId('filter-logic')).toBeNull()
  })

  it('shows logic selector with multiple clauses', () => {
    render(
      <Harness
        initial={{
          logic: 'AND',
          filters: [
            { field: 'status', op: 'equals', value: 'a' },
            { field: 'industry', op: 'equals', value: 'b' },
          ],
        }}
        onChange={vi.fn()}
      />
    )
    expect(screen.getByTestId('filter-logic')).toBeInTheDocument()
  })

  it('emits null when the last clause is removed', async () => {
    const onChange = vi.fn()
    render(
      <Harness
        initial={{ logic: 'AND', filters: [{ field: 'status', op: 'equals', value: 'a' }] }}
        onChange={onChange}
      />
    )
    await userEvent.click(screen.getByTestId('filter-remove-0'))
    expect(onChange).toHaveBeenLastCalledWith(null)
  })

  it('updates clause value via the input', async () => {
    const onChange = vi.fn()
    render(
      <Harness
        initial={{ logic: 'AND', filters: [{ field: 'status', op: 'equals', value: '' }] }}
        onChange={onChange}
      />
    )
    await userEvent.type(screen.getByTestId('filter-value-0'), 'Active')
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        filters: [expect.objectContaining({ value: 'Active' })],
      })
    )
  })
})
