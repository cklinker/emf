/** VariablesSection: static vs computed authoring (app-platform slice 2). */
import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { I18nProvider } from '@/context/I18nContext'
import { VariablesSection } from './VariablesSection'
import type { PageVariable } from '../../pageConfig'

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>{children}</I18nProvider>
)

describe('VariablesSection computed variables', () => {
  it('shows the Default input for a static row and Expression for a computed row', () => {
    const variables: PageVariable[] = [
      { name: 'a', type: 'number', default: 1 },
      { name: 'b', type: 'number', kind: 'computed', expression: 'vars.a + 1' },
    ]
    render(<VariablesSection variables={variables} onChange={vi.fn()} />, { wrapper })
    expect(screen.getByTestId('variable-default-0')).toBeInTheDocument()
    expect(screen.queryByTestId('variable-expression-0')).toBeNull()
    expect(screen.getByTestId('variable-expression-1')).toHaveValue('vars.a + 1')
    expect(screen.queryByTestId('variable-default-1')).toBeNull()
  })

  it('switching kind to computed writes the kind through onChange', () => {
    const onChange = vi.fn()
    render(
      <VariablesSection
        variables={[{ name: 'a', type: 'number', default: 1 }]}
        onChange={onChange}
      />,
      { wrapper }
    )
    fireEvent.change(screen.getByTestId('variable-kind-0'), { target: { value: 'computed' } })
    expect(onChange).toHaveBeenCalledWith([
      { name: 'a', type: 'number', default: 1, kind: 'computed' },
    ])
  })

  it('editing the expression writes it through onChange', () => {
    const onChange = vi.fn()
    render(
      <VariablesSection
        variables={[{ name: 'b', type: 'number', kind: 'computed', expression: '' }]}
        onChange={onChange}
      />,
      { wrapper }
    )
    fireEvent.change(screen.getByTestId('variable-expression-0'), {
      target: { value: 'vars.a * 2' },
    })
    expect(onChange).toHaveBeenCalledWith([
      { name: 'b', type: 'number', kind: 'computed', expression: 'vars.a * 2' },
    ])
  })
})
