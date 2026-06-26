/**
 * ExpressionField (slice 2d finalize). Mocks `FieldExpressionPicker` to drive `onInsert` deterministically
 * and asserts the path↔expr `mode` mapping, the `{{token}}` chip round-trip, and the Clear → literal path.
 */
import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider } from '../../../../context/I18nContext'
import { ExpressionField } from './ExpressionField'
import type { PropFieldSchema } from '../../widgets/types'
import type { PageComponent } from '../../model/pageModel'

// Mock the picker: expose two buttons that emit a field-path token and a function-stub token.
vi.mock('../../../../components/FieldExpressionPicker', () => ({
  FieldExpressionPicker: ({
    open,
    onInsert,
  }: {
    open: boolean
    onInsert: (token: string) => void
  }) =>
    open ? (
      <div data-testid="mock-picker">
        <button type="button" onClick={() => onInsert('account_id.name')} data-testid="pick-field">
          field
        </button>
        <button
          type="button"
          onClick={() => onInsert('IF(count > 0, "a", "b")')}
          data-testid="pick-fn"
        >
          fn
        </button>
      </div>
    ) : null,
}))

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>{children}</I18nProvider>
)
const schema: PropFieldSchema = { key: 'text', label: 'Text', kind: 'expression', bindable: true }
const node: PageComponent = { id: 'c1', type: 'heading', props: {} }

function renderField(value: unknown, onChange = vi.fn()) {
  render(
    <ExpressionField
      schema={schema}
      value={value as never}
      onChange={onChange}
      node={node}
      fieldId="property-text"
    />,
    { wrapper }
  )
  return onChange
}

describe('ExpressionField', () => {
  it('shows the bound token wrapped in {{…}}', () => {
    renderField({ $bind: 'record.name', mode: 'path' })
    expect(screen.getByTestId('bindable-expr-property-text')).toHaveTextContent('{{record.name}}')
  })

  it('Edit opens the picker; a field-path insert writes mode:path', async () => {
    const onChange = renderField('')
    await userEvent.click(screen.getByTestId('bindable-edit-property-text'))
    expect(screen.getByTestId('mock-picker')).toBeInTheDocument()
    await userEvent.click(screen.getByTestId('pick-field'))
    expect(onChange).toHaveBeenCalledWith({ $bind: 'account_id.name', mode: 'path' })
  })

  it('a function-stub insert writes mode:expr', async () => {
    const onChange = renderField('')
    await userEvent.click(screen.getByTestId('bindable-edit-property-text'))
    await userEvent.click(screen.getByTestId('pick-fn'))
    expect(onChange).toHaveBeenCalledWith({ $bind: 'IF(count > 0, "a", "b")', mode: 'expr' })
  })

  it('Clear reverts a binding to a literal empty string', async () => {
    const onChange = renderField({ $bind: 'record.name', mode: 'path' })
    await userEvent.click(screen.getByTestId('bindable-clear-property-text'))
    expect(onChange).toHaveBeenCalledWith('')
  })

  it('shows no Clear button when there is no binding', () => {
    renderField('')
    expect(screen.queryByTestId('bindable-clear-property-text')).not.toBeInTheDocument()
  })
})
