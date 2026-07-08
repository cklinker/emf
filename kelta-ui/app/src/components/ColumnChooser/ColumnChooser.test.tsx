import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ColumnChooser } from './ColumnChooser'
import { I18nProvider } from '../../context/I18nContext'

const FIELDS = [
  { name: 'name', displayName: 'Name' },
  { name: 'stage', displayName: 'Stage' },
  { name: 'amount', displayName: 'Amount' },
]

function renderChooser(visible: string[], onChange = vi.fn()) {
  render(
    <I18nProvider>
      <ColumnChooser fields={FIELDS} visibleColumns={visible} onChange={onChange} />
    </I18nProvider>
  )
  fireEvent.click(screen.getByTestId('column-chooser-trigger'))
  return onChange
}

describe('ColumnChooser', () => {
  it('toggles a hidden column on', () => {
    const onChange = renderChooser(['name'])
    fireEvent.click(screen.getByLabelText('Toggle column Stage'))
    expect(onChange).toHaveBeenCalledWith(['name', 'stage'])
  })

  it('toggles a visible column off', () => {
    const onChange = renderChooser(['name', 'stage'])
    fireEvent.click(screen.getByLabelText('Toggle column Name'))
    expect(onChange).toHaveBeenCalledWith(['stage'])
  })

  it('never drops below one visible column', () => {
    const onChange = renderChooser(['amount'])
    fireEvent.click(screen.getByLabelText('Toggle column Amount'))
    expect(onChange).not.toHaveBeenCalled()
  })

  it('moves a column up', () => {
    const onChange = renderChooser(['name', 'stage', 'amount'])
    fireEvent.click(screen.getByTestId('column-up-stage'))
    expect(onChange).toHaveBeenCalledWith(['stage', 'name', 'amount'])
  })

  it('moves a column down (controlled props stay as rendered)', () => {
    const onChange = renderChooser(['name', 'stage', 'amount'])
    fireEvent.click(screen.getByTestId('column-down-stage'))
    expect(onChange).toHaveBeenCalledWith(['name', 'amount', 'stage'])
  })
})
