/**
 * Palette tests. The palette renders from `widgetRegistry.listByCategory()` — one section per non-empty
 * category, a tile per built-in. Plugin page components are NOT listed here (the palette lists built-in
 * widgets only). Click adds; drag-start fires the drag callback.
 */
import React from 'react'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider } from '../../../context/I18nContext'
import { componentRegistry } from '../../../services/componentRegistry'
import { Palette } from './Palette'
import '../widgets/builtins'

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>{children}</I18nProvider>
)

describe('Palette', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders all 8 built-ins as palette items', () => {
    render(<Palette onDragStart={vi.fn()} onAddComponent={vi.fn()} />, { wrapper })
    for (const type of [
      'heading',
      'text',
      'button',
      'image',
      'table',
      'form',
      'card',
      'container',
    ]) {
      expect(screen.getByTestId(`palette-item-${type}`)).toBeInTheDocument()
    }
  })

  it('groups widgets by category and omits empty categories (navigation absent)', () => {
    render(<Palette onDragStart={vi.fn()} onAddComponent={vi.fn()} />, { wrapper })
    expect(screen.getByTestId('palette-category-layout')).toBeInTheDocument()
    expect(screen.getByTestId('palette-category-content')).toBeInTheDocument()
    expect(screen.getByTestId('palette-category-data')).toBeInTheDocument()
    expect(screen.getByTestId('palette-category-input')).toBeInTheDocument()
    // navigation/chart have no built-ins → no section.
    expect(screen.queryByTestId('palette-category-navigation')).not.toBeInTheDocument()
    expect(screen.queryByTestId('palette-category-chart')).not.toBeInTheDocument()
  })

  it('places form under the input category, not data', () => {
    render(<Palette onDragStart={vi.fn()} onAddComponent={vi.fn()} />, { wrapper })
    const inputSection = screen.getByTestId('palette-category-input').parentElement as HTMLElement
    const dataSection = screen.getByTestId('palette-category-data').parentElement as HTMLElement
    expect(within(inputSection).getByTestId('palette-item-form')).toBeInTheDocument()
    expect(within(dataSection).getByTestId('palette-item-table')).toBeInTheDocument()
    expect(within(dataSection).queryByTestId('palette-item-form')).not.toBeInTheDocument()
  })

  it('clicking a tile calls onAddComponent with the type', async () => {
    const onAddComponent = vi.fn()
    render(<Palette onDragStart={vi.fn()} onAddComponent={onAddComponent} />, { wrapper })
    await userEvent.click(screen.getByTestId('palette-item-heading'))
    expect(onAddComponent).toHaveBeenCalledWith('heading')
  })

  it('drag start fires onDragStart with the type', () => {
    const onDragStart = vi.fn()
    render(<Palette onDragStart={onDragStart} onAddComponent={vi.fn()} />, { wrapper })
    fireEvent.dragStart(screen.getByTestId('palette-item-button'))
    expect(onDragStart).toHaveBeenCalledWith('button')
  })

  it('does NOT list plugin page components (palette is built-ins only)', () => {
    const Plugin = () => <div>plugin</div>
    componentRegistry.registerPageComponent('my-plugin-widget', Plugin)
    try {
      render(<Palette onDragStart={vi.fn()} onAddComponent={vi.fn()} />, { wrapper })
      expect(screen.queryByTestId('palette-item-my-plugin-widget')).not.toBeInTheDocument()
    } finally {
      componentRegistry.clear()
    }
  })
})
