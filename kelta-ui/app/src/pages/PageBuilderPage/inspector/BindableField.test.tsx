/**
 * BindableField fx literal↔expression toggle write-contract tests. The load-bearing transitions:
 * literal → expr writes `{ $bind:'', mode:'expr' }`; expr → literal writes the `literalDefault`; a Binding
 * value mounts in expression mode (shows the expr chip); a scalar mounts in literal mode (shows the editor).
 */
import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider } from '../../../context/I18nContext'
import { BindableField } from './BindableField'
import { TextField } from './fields/TextField'
import type { PageComponent, PropValue } from '../model/pageModel'
import type { PropFieldSchema } from '../widgets/types'

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>{children}</I18nProvider>
)

const node: PageComponent = { id: 'c1', type: 'heading', props: {} }
const schema: PropFieldSchema = { key: 'text', label: 'Text', kind: 'text', bindable: true }

function renderBindable(value: PropValue | undefined, onChange = vi.fn()) {
  render(
    <BindableField
      schema={schema}
      value={value}
      onChange={onChange}
      node={node}
      fieldId="property-text"
      renderLiteral={({ value: v, onChange: oc }) => (
        <TextField schema={schema} value={v} onChange={oc} node={node} fieldId="property-text" />
      )}
      literalDefault="Heading"
    />,
    { wrapper }
  )
  return onChange
}

describe('BindableField', () => {
  it('mounts in literal mode for a scalar value (shows the literal editor)', () => {
    renderBindable('Orders')
    expect(screen.getByTestId('property-text')).toHaveValue('Orders')
    expect(screen.queryByTestId('bindable-expr-property-text')).not.toBeInTheDocument()
  })

  it('mounts in expression mode for a Binding value (shows the expr chip)', () => {
    renderBindable({ $bind: 'record.name', mode: 'expr' })
    expect(screen.getByTestId('bindable-expr-property-text')).toHaveTextContent('{{record.name}}')
  })

  it('literal → expr: clicking fx writes an empty binding', async () => {
    const onChange = renderBindable('Orders')
    await userEvent.click(screen.getByTestId('bindable-fx-text'))
    expect(onChange).toHaveBeenCalledWith({ $bind: '', mode: 'expr' })
  })

  it('expr → literal: clicking fx writes the literalDefault and drops the binding', async () => {
    const onChange = renderBindable({ $bind: 'record.name', mode: 'expr' })
    await userEvent.click(screen.getByTestId('bindable-fx-text'))
    expect(onChange).toHaveBeenCalledWith('Heading')
  })

  it('always renders the fx toggle (Inspector decides whether to wrap a field at all)', () => {
    renderBindable('Orders')
    expect(screen.getByTestId('bindable-fx-text')).toBeInTheDocument()
  })
})
