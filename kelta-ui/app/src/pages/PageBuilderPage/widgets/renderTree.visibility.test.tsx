/**
 * Conditional visibility in the shared render path (app-platform slice 1): runtime
 * skips a node whose `visible` prop resolves hidden; the editor never hides; absent
 * prop = today's behavior.
 */
import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { I18nProvider } from '@/context/I18nContext'
import { RenderTree } from './renderTree'
import { registerBuiltinWidgets } from './builtins'
import type { BindingScope } from '../model/bindingScope'
import type { RenderNode } from './types'

function renderTree(nodes: RenderNode[], scope: BindingScope, mode: 'editor' | 'runtime') {
  return render(
    <I18nProvider>
      <RenderTree components={nodes} tenantSlug="acme" scope={scope} mode={mode} />
    </I18nProvider>
  )
}

const heading = (visible: unknown): RenderNode[] => [
  {
    id: 'h',
    type: 'heading',
    props: visible === undefined ? { text: 'Hello' } : { text: 'Hello', visible: visible as never },
  },
]

describe('RenderTree conditional visibility', () => {
  beforeEach(() => registerBuiltinWidgets())

  it('renders a node without a visible prop (legacy behavior)', () => {
    renderTree(heading(undefined), {}, 'runtime')
    expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Hello')
  })

  it('hides a literal visible:false at runtime', () => {
    renderTree(heading(false), {}, 'runtime')
    expect(screen.queryByTestId('page-node-heading')).toBeNull()
  })

  it('hides when a bound visible resolves false', () => {
    renderTree(heading({ $bind: 'vars.show', mode: 'path' }), { vars: { show: false } }, 'runtime')
    expect(screen.queryByTestId('page-node-heading')).toBeNull()
  })

  it('hides when a bound visible cannot resolve (null — fail-closed)', () => {
    renderTree(heading({ $bind: 'vars.missing', mode: 'path' }), {}, 'runtime')
    expect(screen.queryByTestId('page-node-heading')).toBeNull()
  })

  it('shows when a bound visible resolves true', () => {
    renderTree(heading({ $bind: 'vars.show', mode: 'path' }), { vars: { show: true } }, 'runtime')
    expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Hello')
  })

  it('never hides in editor mode (ghosting is SelectableNode chrome)', () => {
    renderTree(heading(false), {}, 'editor')
    expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Hello')
  })

  it('hides a container subtree wholesale', () => {
    const nodes: RenderNode[] = [
      {
        id: 'c',
        type: 'container',
        props: { visible: false as never },
        children: [{ id: 'h2', type: 'heading', props: { text: 'Nested' } }],
      },
    ]
    renderTree(nodes, {}, 'runtime')
    expect(screen.queryByText('Nested')).toBeNull()
  })
})
