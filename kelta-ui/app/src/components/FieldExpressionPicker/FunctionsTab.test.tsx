import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FunctionsTab } from './FunctionsTab'

describe('FunctionsTab', () => {
  it('renders functions grouped by category', () => {
    render(<FunctionsTab onInsert={() => {}} />)
    expect(screen.getByText('Logical')).toBeInTheDocument()
    expect(screen.getByText('Text')).toBeInTheDocument()
    expect(screen.getByText('Math')).toBeInTheDocument()
    expect(screen.getByText('Date & time')).toBeInTheDocument()
  })

  it('inserts the function stub when a row is clicked', async () => {
    const onInsert = vi.fn()
    render(<FunctionsTab onInsert={onInsert} />)
    const upper = screen.getByTestId('field-expression-picker-functions-fn-UPPER')
    await userEvent.click(upper)
    expect(onInsert).toHaveBeenCalledWith('UPPER(${text})')
  })

  it('filters by name', async () => {
    const onInsert = vi.fn()
    render(<FunctionsTab onInsert={onInsert} />)
    const search = screen.getByPlaceholderText('Search functions…')
    await userEvent.type(search, 'today')
    expect(screen.getByTestId('field-expression-picker-functions-fn-TODAY')).toBeInTheDocument()
    expect(screen.queryByTestId('field-expression-picker-functions-fn-UPPER')).toBeNull()
  })

  it('filters by allowedReturnTypes', () => {
    const onInsert = vi.fn()
    render(<FunctionsTab onInsert={onInsert} allowedReturnTypes={['boolean']} />)
    // AND returns boolean → visible.
    expect(screen.getByTestId('field-expression-picker-functions-fn-AND')).toBeInTheDocument()
    // ROUND returns number → hidden.
    expect(screen.queryByTestId('field-expression-picker-functions-fn-ROUND')).toBeNull()
    // 'any' return-type functions still surface.
    expect(screen.getByTestId('field-expression-picker-functions-fn-IF')).toBeInTheDocument()
  })

  it('expands the docs panel when the help button is pressed', async () => {
    render(<FunctionsTab onInsert={() => {}} />)
    const helpButton = screen.getByLabelText('Show details for IF')
    await userEvent.click(helpButton)
    expect(screen.getByText('Example:')).toBeInTheDocument()
    expect(screen.getByText('IF(amount > 100, "Big order", "Small order")')).toBeInTheDocument()
  })
})
