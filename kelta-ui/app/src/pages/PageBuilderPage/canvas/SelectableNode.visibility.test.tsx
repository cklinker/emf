/** SelectableNode visibility chrome (app-platform slice 1): ghost + badge, never hides. */
import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DndContext } from '@dnd-kit/core'
import { SortableContext } from '@dnd-kit/sortable'
import { I18nProvider } from '@/context/I18nContext'
import { SelectableNode } from './SelectableNode'
import { registerBuiltinWidgets } from '../widgets/builtins'
import { NODE_ID } from './dnd/types'
import type { PageComponent } from '../model/pageModel'

function renderNode(node: PageComponent) {
  return render(
    <I18nProvider>
      <DndContext>
        <SortableContext items={[NODE_ID(node.id)]}>
          <SelectableNode
            node={node}
            parentId={null}
            inGrid={false}
            selectedId={null}
            tenantSlug="acme"
            onSelect={vi.fn()}
            onDelete={vi.fn()}
            onSpanChange={vi.fn()}
          />
        </SortableContext>
      </DndContext>
    </I18nProvider>
  )
}

const make = (props: Record<string, unknown>): PageComponent => ({
  id: 'n1',
  type: 'heading',
  props: { text: 'Hi', ...props },
})

describe('SelectableNode visibility chrome', () => {
  beforeEach(() => registerBuiltinWidgets())

  it('shows no badge without a visible prop', () => {
    renderNode(make({}))
    expect(screen.queryByTestId('visibility-badge-n1')).toBeNull()
  })

  it('ghosts + badges a literal visible:false but still renders the node', () => {
    renderNode(make({ visible: false }))
    const badge = screen.getByTestId('visibility-badge-n1')
    expect(badge).toHaveTextContent(/hidden/i)
    expect(screen.getByTestId('canvas-component-n1').className).toContain('opacity-50')
    // Node body still renders (editor never hides)
    expect(screen.getByText('Hi')).toBeInTheDocument()
  })

  it('badges a bound visible as conditional without ghosting', () => {
    renderNode(make({ visible: { $bind: 'vars.show', mode: 'expr' } }))
    expect(screen.getByTestId('visibility-badge-n1')).toHaveTextContent(/conditional/i)
    expect(screen.getByTestId('canvas-component-n1').className).not.toContain('opacity-50')
  })
})
