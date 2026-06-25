/**
 * Palette tests. The palette renders from `widgetRegistry.listByCategory()` — one section per non-empty
 * category, a tile per built-in. Plugin page components are NOT listed here (the palette lists built-in
 * widgets only). Click adds; each tile is a `@dnd-kit` draggable source (slice 2c), so the palette is
 * rendered inside a `DndContext` here.
 */
import React from 'react'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, within, fireEvent } from '@testing-library/react'
import { DndContext } from '@dnd-kit/core'
import { I18nProvider } from '../../../context/I18nContext'
import { componentRegistry } from '../../../services/componentRegistry'
import { Palette } from './Palette'
import '../widgets/builtins'

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>
    <DndContext>{children}</DndContext>
  </I18nProvider>
)

describe('Palette', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the layout, content, data and input built-ins as palette items', () => {
    render(<Palette onAddComponent={vi.fn()} />, { wrapper })
    for (const type of [
      // layout (slice 2c adds grid/row/column/divider alongside card/container)
      'grid',
      'row',
      'column',
      'divider',
      'card',
      'container',
      // content
      'heading',
      'text',
      'button',
      'image',
      // data / input
      'table',
      'form',
    ]) {
      expect(screen.getByTestId(`palette-item-${type}`)).toBeInTheDocument()
    }
  })

  it('groups widgets by category and omits empty categories (navigation absent)', () => {
    render(<Palette onAddComponent={vi.fn()} />, { wrapper })
    expect(screen.getByTestId('palette-category-layout')).toBeInTheDocument()
    expect(screen.getByTestId('palette-category-content')).toBeInTheDocument()
    expect(screen.getByTestId('palette-category-data')).toBeInTheDocument()
    expect(screen.getByTestId('palette-category-input')).toBeInTheDocument()
    // navigation/chart have no built-ins → no section.
    expect(screen.queryByTestId('palette-category-navigation')).not.toBeInTheDocument()
    expect(screen.queryByTestId('palette-category-chart')).not.toBeInTheDocument()
  })

  it('places form under the input category, not data', () => {
    render(<Palette onAddComponent={vi.fn()} />, { wrapper })
    const inputSection = screen.getByTestId('palette-category-input').parentElement as HTMLElement
    const dataSection = screen.getByTestId('palette-category-data').parentElement as HTMLElement
    expect(within(inputSection).getByTestId('palette-item-form')).toBeInTheDocument()
    expect(within(dataSection).getByTestId('palette-item-table')).toBeInTheDocument()
    expect(within(dataSection).queryByTestId('palette-item-form')).not.toBeInTheDocument()
  })

  it('lists the layout widgets under the layout category', () => {
    render(<Palette onAddComponent={vi.fn()} />, { wrapper })
    const layoutSection = screen.getByTestId('palette-category-layout').parentElement as HTMLElement
    for (const type of ['grid', 'row', 'column', 'divider']) {
      expect(within(layoutSection).getByTestId(`palette-item-${type}`)).toBeInTheDocument()
    }
  })

  it('clicking a tile calls onAddComponent with the type', () => {
    const onAddComponent = vi.fn()
    render(<Palette onAddComponent={onAddComponent} />, { wrapper })
    // fireEvent.click (vs userEvent's pointer sequence) avoids dnd-kit's default-sensor drag activation.
    fireEvent.click(screen.getByTestId('palette-item-heading'))
    expect(onAddComponent).toHaveBeenCalledWith('heading')
  })

  it('each tile is a draggable source (dnd-kit attributes present)', () => {
    render(<Palette onAddComponent={vi.fn()} />, { wrapper })
    const tile = screen.getByTestId('palette-item-button')
    // useDraggable sets a roledescription on the draggable element.
    expect(tile).toHaveAttribute('aria-roledescription', 'draggable')
  })

  it('does NOT list plugin page components (palette is built-ins only)', () => {
    const Plugin = () => <div>plugin</div>
    componentRegistry.registerPageComponent('my-plugin-widget', Plugin)
    try {
      render(<Palette onAddComponent={vi.fn()} />, { wrapper })
      expect(screen.queryByTestId('palette-item-my-plugin-widget')).not.toBeInTheDocument()
    } finally {
      componentRegistry.clear()
    }
  })
})
