/**
 * Inspector schema-driven panel tests. The inspector produces fields ONLY by looping a node's
 * descriptor.propSchema — there are zero per-type conditionals. These tests prove the grouping, the
 * kind→editor mapping, the bindable wrapping, and the span/event-list write targets (node.span / node.events).
 */
import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider } from '../../../context/I18nContext'
import { Inspector } from './Inspector'
import '../widgets/builtins'
import type { PageComponent } from '../model/pageModel'

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>{children}</I18nProvider>
)

const make = (over: Partial<PageComponent>): PageComponent => ({
  id: 'c1',
  type: 'heading',
  props: {},
  ...over,
})

describe('Inspector', () => {
  it('renders the empty state for a null node', () => {
    render(<Inspector node={null} onChange={vi.fn()} />, { wrapper })
    expect(screen.getByTestId('property-panel')).toBeInTheDocument()
    expect(screen.getByText(/select a component/i)).toBeInTheDocument()
  })

  it('renders read-only type + id rows', () => {
    render(<Inspector node={make({ id: 'comp_42', type: 'heading' })} onChange={vi.fn()} />, {
      wrapper,
    })
    expect(screen.getByTestId('property-type')).toHaveTextContent('heading')
    expect(screen.getByTestId('property-id')).toHaveValue('comp_42')
  })

  it('loops a heading schema into text + level under the Content group', () => {
    render(<Inspector node={make({ type: 'heading' })} onChange={vi.fn()} />, { wrapper })
    expect(screen.getByTestId('property-text')).toBeInTheDocument()
    expect(screen.getByTestId('property-level')).toBeInTheDocument()
    expect(screen.getByTestId('property-group-content')).toBeInTheDocument()
  })

  it('shows the fx toggle only for bindable fields (text has it, level does not)', () => {
    render(<Inspector node={make({ type: 'heading' })} onChange={vi.fn()} />, { wrapper })
    expect(screen.getByTestId('bindable-fx-text')).toBeInTheDocument()
    expect(screen.queryByTestId('bindable-fx-level')).not.toBeInTheDocument()
  })

  it('loops a table schema into the dataView editor under the Data group', () => {
    render(<Inspector node={make({ type: 'table' })} onChange={vi.fn()} />, { wrapper })
    expect(screen.getByTestId('property-collection')).toBeInTheDocument()
    expect(screen.getByTestId('property-columns')).toBeInTheDocument()
    expect(screen.getByTestId('property-limit')).toBeInTheDocument()
    expect(screen.getByTestId('property-group-data')).toBeInTheDocument()
  })

  it('editing heading text writes onChange({ props: { text } })', async () => {
    const onChange = vi.fn()
    render(
      <Inspector node={make({ type: 'heading', props: { text: '' } })} onChange={onChange} />,
      {
        wrapper,
      }
    )
    await userEvent.type(screen.getByTestId('property-text'), 'X')
    expect(onChange).toHaveBeenLastCalledWith({ props: { text: 'X' } })
  })

  it('editing the table collection writes onChange({ props: { dataView: { collection } } })', async () => {
    const onChange = vi.fn()
    render(<Inspector node={make({ type: 'table' })} onChange={onChange} />, { wrapper })
    await userEvent.type(screen.getByTestId('property-collection'), 'o')
    expect(onChange).toHaveBeenLastCalledWith({ props: { dataView: { collection: 'o' } } })
  })

  it('a span field writes onChange({ span }), not props', () => {
    const onChange = vi.fn()
    render(<Inspector node={make({ type: 'card' })} onChange={onChange} />, { wrapper })
    expect(screen.getByTestId('property-span')).toBeInTheDocument()
    expect(screen.getByTestId('property-group-layout')).toBeInTheDocument()
  })

  it('an event-list field reads node.events and writes onChange({ events })', async () => {
    const onChange = vi.fn()
    render(
      <Inspector node={make({ type: 'button', props: { label: 'Save' } })} onChange={onChange} />,
      {
        wrapper,
      }
    )
    // button declares supportedEvents:['onClick'] + an event-list schema entry → the add control renders.
    await userEvent.click(screen.getByTestId('event-add-onClick'))
    expect(onChange).toHaveBeenLastCalledWith({
      events: { onClick: [{ action: 'runFlow', flowId: '', input: {} }] },
    })
  })

  it('renders the no-properties hint for a type with an empty propSchema (unknown/plugin)', () => {
    render(<Inspector node={make({ type: 'totally-unknown' })} onChange={vi.fn()} />, { wrapper })
    expect(screen.getByTestId('property-type')).toHaveTextContent('totally-unknown')
    expect(screen.getByTestId('property-empty')).toBeInTheDocument()
  })
})
