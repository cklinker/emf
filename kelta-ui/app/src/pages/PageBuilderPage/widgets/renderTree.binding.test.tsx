/**
 * Binding-aware render path (slice 2d). The now-real `resolveBindings` resolves bound props before a
 * descriptor's `Render`, and `interpolate` handles inline `{{…}}` tags — identically in runtime and
 * editor mode (the 2a de-dup guarantee preserved under binding).
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

describe('RenderTree binding resolution', () => {
  beforeEach(() => registerBuiltinWidgets())

  it('resolves a bound heading text from scope.record (runtime)', () => {
    const nodes: RenderNode[] = [
      {
        id: 'h',
        type: 'heading',
        props: { text: { $bind: 'record.name', mode: 'path' }, level: 'h1' },
      },
    ]
    renderTree(nodes, { record: { name: 'Acme' } }, 'runtime')
    const heading = screen.getByTestId('page-node-heading')
    expect(heading).toHaveTextContent('Acme')
    expect(heading.tagName).toBe('H1')
  })

  it('resolves the same bound heading identically in editor mode', () => {
    const nodes: RenderNode[] = [
      { id: 'h', type: 'heading', props: { text: { $bind: 'record.name', mode: 'path' } } },
    ]
    renderTree(nodes, { record: { name: 'Acme' } }, 'editor')
    expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Acme')
  })

  it('interpolates an inline {{…}} tag in a literal text prop', () => {
    const nodes: RenderNode[] = [
      { id: 't', type: 'text', props: { content: 'Showing {{data.label}}' } },
    ]
    renderTree(nodes, { data: { label: 'Acme' } }, 'runtime')
    expect(screen.getByTestId('page-node-text')).toHaveTextContent('Showing Acme')
  })

  it('evaluates an expr-mode binding through the formula engine', () => {
    const nodes: RenderNode[] = [
      {
        id: 't',
        type: 'text',
        props: { content: { $bind: 'IF(count > 0, "Has rows", "Empty")', mode: 'expr' } },
      },
    ]
    renderTree(nodes, { vars: { count: 2 } }, 'runtime')
    expect(screen.getByTestId('page-node-text')).toHaveTextContent('Has rows')
  })
})
