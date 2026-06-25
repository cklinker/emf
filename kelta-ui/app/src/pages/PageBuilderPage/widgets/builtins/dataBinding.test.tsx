/**
 * field-value + list/repeater descriptor tests (slice 2d). Rendered through the shared `RenderTree` so
 * the per-row `item` scope is built by the real `renderNode` path.
 */
import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { I18nProvider } from '@/context/I18nContext'
import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import { MAX_REPEATER_ROWS } from '../../model/limits'
import type { BindingScope } from '../../model/bindingScope'
import type { RenderNode } from '../types'

function renderTree(nodes: RenderNode[], scope: BindingScope) {
  return render(
    <I18nProvider>
      <RenderTree components={nodes} tenantSlug="acme" scope={scope} mode="runtime" />
    </I18nProvider>
  )
}

describe('field-value widget', () => {
  beforeEach(() => registerBuiltinWidgets())

  it('renders the resolved source through FieldRenderer (email)', () => {
    const nodes: RenderNode[] = [
      {
        id: 'fv',
        type: 'field-value',
        props: { source: { $bind: 'record.email', mode: 'path' }, fieldType: 'email' },
      },
    ]
    renderTree(nodes, { record: { email: 'ada@example.com' } })
    const el = screen.getByTestId('page-node-field-value')
    expect(el).toHaveTextContent('ada@example.com')
  })

  it('defaults to type string when fieldType is absent', () => {
    const nodes: RenderNode[] = [
      { id: 'fv', type: 'field-value', props: { source: { $bind: 'record.name', mode: 'path' } } },
    ]
    renderTree(nodes, { record: { name: 'Acme' } })
    expect(screen.getByTestId('page-node-field-value')).toHaveTextContent('Acme')
  })
})

describe('list / repeater widget', () => {
  beforeEach(() => registerBuiltinWidgets())

  const repeaterTree = (type: 'repeater' | 'list'): RenderNode[] => [
    {
      id: 'rep',
      type,
      props: { source: { $bind: 'data.accounts', mode: 'path' } },
      children: [
        {
          id: 'h',
          type: 'heading',
          props: { text: { $bind: 'item.name', mode: 'path' }, level: 'h3' },
        },
        { id: 't', type: 'text', props: { content: 'Status: {{item.status}}' } },
      ],
    },
  ]

  it('renders the child subtree once per row with the per-row item scope', () => {
    renderTree(repeaterTree('repeater'), {
      data: {
        accounts: [
          { name: 'Acme', status: 'open' },
          { name: 'Globex', status: 'closed' },
        ],
      },
    })
    const rows = screen.getAllByTestId('page-node-repeater-row')
    expect(rows).toHaveLength(2)
    const headings = screen.getAllByTestId('page-node-heading')
    expect(headings[0]).toHaveTextContent('Acme')
    expect(headings[1]).toHaveTextContent('Globex')
    const texts = screen.getAllByTestId('page-node-text')
    expect(texts[0]).toHaveTextContent('Status: open')
    expect(texts[1]).toHaveTextContent('Status: closed')
  })

  it('aliases record to the current row (record.name resolves inside a row)', () => {
    const nodes: RenderNode[] = [
      {
        id: 'rep',
        type: 'repeater',
        props: { source: { $bind: 'data.accounts', mode: 'path' } },
        children: [
          { id: 'h', type: 'heading', props: { text: { $bind: 'record.name', mode: 'path' } } },
        ],
      },
    ]
    renderTree(nodes, { data: { accounts: [{ name: 'Acme' }] } })
    expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Acme')
  })

  it('renders zero rows for an empty/absent array without crashing', () => {
    renderTree(repeaterTree('repeater'), { data: { accounts: [] } })
    expect(screen.getByTestId('page-node-repeater')).toBeInTheDocument()
    expect(screen.queryByTestId('page-node-repeater-row')).not.toBeInTheDocument()

    renderTree(repeaterTree('repeater'), {})
    expect(screen.getAllByTestId('page-node-repeater').length).toBeGreaterThan(0)
  })

  it('list alias renders identically to repeater', () => {
    renderTree(repeaterTree('list'), { data: { accounts: [{ name: 'Acme', status: 'open' }] } })
    expect(screen.getAllByTestId('page-node-repeater-row')).toHaveLength(1)
    expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Acme')
  })

  it('caps rendered rows at MAX_REPEATER_ROWS and shows a truncation note', () => {
    const accounts = Array.from({ length: MAX_REPEATER_ROWS + 50 }, (_, i) => ({ name: `A${i}` }))
    renderTree(
      [
        {
          id: 'rep',
          type: 'repeater',
          props: { source: { $bind: 'data.accounts', mode: 'path' } },
          children: [
            { id: 'h', type: 'heading', props: { text: { $bind: 'item.name', mode: 'path' } } },
          ],
        },
      ],
      { data: { accounts } }
    )
    expect(screen.getAllByTestId('page-node-repeater-row')).toHaveLength(MAX_REPEATER_ROWS)
    const note = screen.getByTestId('page-node-repeater-truncated')
    expect(note).toHaveTextContent(String(MAX_REPEATER_ROWS))
    expect(note).toHaveTextContent(String(MAX_REPEATER_ROWS + 50))
  })

  it('does not show a truncation note at or below the cap', () => {
    renderTree(repeaterTree('repeater'), { data: { accounts: [{ name: 'A', status: 'x' }] } })
    expect(screen.queryByTestId('page-node-repeater-truncated')).not.toBeInTheDocument()
  })
})
