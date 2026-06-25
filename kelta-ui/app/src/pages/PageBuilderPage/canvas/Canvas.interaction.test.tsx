/**
 * Interaction tests for the dnd-kit canvas (slice 2c). Pointer DnD is flaky in jsdom, so the actual
 * insert/move/reorder routing is unit-tested in `dnd/useCanvasDnd.test.ts`; here we assert the canvas
 * RENDERS the tree + drop zones, preserves the selection/delete chrome + testids, and exposes the a11y
 * affordances (drag-handle buttons with aria-labels, the dnd-kit announcer live region).
 */
import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider } from '../../../context/I18nContext'
import { Canvas } from './Canvas'
import { Palette } from '../palette/Palette'
import type { PageComponent } from '../model/pageModel'
import '../widgets/builtins'

function tree(): PageComponent[] {
  return [
    {
      id: 'g',
      type: 'grid',
      props: {},
      children: [
        {
          id: 'c1',
          type: 'column',
          props: {},
          span: { base: 6 },
          children: [{ id: 'h', type: 'heading', props: { text: 'Orders', level: 'h2' } }],
        },
        { id: 'c2', type: 'column', props: {}, span: { base: 6 }, children: [] },
      ],
    },
  ]
}

function renderCanvas(overrides: Partial<React.ComponentProps<typeof Canvas>> = {}) {
  const props: React.ComponentProps<typeof Canvas> = {
    components: tree(),
    selectedId: null,
    onSelect: vi.fn(),
    onChange: vi.fn(),
    onDelete: vi.fn(),
    tenantSlug: 'acme',
    palette: <Palette onAddComponent={vi.fn()} />,
    ...overrides,
  }
  return { props, ...render(<Canvas {...props} />, { wrapper: I18nProvider }) }
}

describe('Canvas (dnd-kit)', () => {
  it('renders the page-canvas and the node tree with preserved testids', () => {
    renderCanvas()
    expect(screen.getByTestId('page-canvas')).toBeInTheDocument()
    expect(screen.getByTestId('canvas-component-g')).toBeInTheDocument()
    expect(screen.getByTestId('canvas-component-c1')).toBeInTheDocument()
    expect(screen.getByTestId('canvas-component-h')).toBeInTheDocument()
  })

  it('renders a droppable container for each container node (drop zones)', () => {
    renderCanvas()
    expect(screen.getByTestId('container-root')).toBeInTheDocument()
    expect(screen.getByTestId('container-g')).toBeInTheDocument()
    expect(screen.getByTestId('container-c1')).toBeInTheDocument()
    expect(screen.getByTestId('container-c2')).toBeInTheDocument()
  })

  it('shows the palette tiles as draggable sources inside the canvas DndContext', () => {
    renderCanvas()
    expect(screen.getByTestId('palette-item-grid')).toHaveAttribute(
      'aria-roledescription',
      'draggable'
    )
  })

  it('clicking a node fires onSelect with its id', async () => {
    const onSelect = vi.fn()
    renderCanvas({ onSelect })
    await userEvent.click(screen.getByTestId('node-body-h'))
    expect(onSelect).toHaveBeenCalledWith('h')
  })

  it('the × button fires onDelete and keeps the delete-component testid', async () => {
    const onDelete = vi.fn()
    renderCanvas({ onDelete })
    expect(screen.getByTestId('delete-component-h')).toBeInTheDocument()
    await userEvent.click(screen.getByTestId('delete-component-h'))
    expect(onDelete).toHaveBeenCalledWith('h')
  })

  it('each node has a keyboard-accessible drag handle <button> with an aria-label', () => {
    renderCanvas()
    const handle = screen.getByTestId('drag-handle-h')
    expect(handle.tagName).toBe('BUTTON')
    expect(handle).toHaveAttribute('aria-label')
    expect(handle.getAttribute('aria-label')).toMatch(/heading/i)
  })

  it('a child of a grid/row shows a resize handle; a leaf of a column does not', () => {
    renderCanvas()
    // c1/c2 are children of the grid → resizable.
    expect(screen.getByTestId('resize-handle-c1')).toBeInTheDocument()
    // 'h' is a child of a column (not a grid/row) → no resize handle.
    expect(screen.queryByTestId('resize-handle-h')).not.toBeInTheDocument()
  })

  it('exposes the dnd-kit screen-reader announcer live region', () => {
    const { container } = renderCanvas()
    // dnd-kit renders a visually-hidden aria-live region for announcements.
    expect(container.ownerDocument.querySelector('[aria-live]')).toBeTruthy()
  })

  it('renders the empty-state hint and a root droppable when there are no components', () => {
    renderCanvas({ components: [] })
    expect(screen.getByTestId('container-root')).toBeInTheDocument()
    expect(screen.getByText(/no components added yet/i)).toBeInTheDocument()
  })
})
