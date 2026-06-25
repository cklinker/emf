/**
 * Icon widget (slice 2g). Resolves a lucide icon by name (PascalCase via kebab/snake conversion), applies
 * size/color, and degrades to the `HelpCircle` fallback for an unknown name (no throw).
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nProvider } from '@/context/I18nContext'
import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import type { RenderNode } from '../types'

function renderIcon(node: RenderNode) {
  return render(
    <I18nProvider>
      <RenderTree components={[node]} tenantSlug="acme" mode="runtime" />
    </I18nProvider>
  )
}

beforeEach(() => {
  registerBuiltinWidgets()
})

describe('icon widget', () => {
  it('renders a known icon as an svg with size and color applied', () => {
    renderIcon({ id: 'ic1', type: 'icon', props: { name: 'star', size: 32, color: '#3B82F6' } })
    const icon = screen.getByTestId('page-node-icon')
    expect(icon.tagName.toLowerCase()).toBe('svg')
    expect(icon).toHaveAttribute('width', '32')
    expect(icon).toHaveAttribute('stroke', '#3B82F6')
  })

  it('converts kebab/snake names to PascalCase for lookup', () => {
    renderIcon({ id: 'ic2', type: 'icon', props: { name: 'shopping-cart' } })
    // ShoppingCart resolves (not the fallback) — lucide tags the icon class with its name.
    const icon = screen.getByTestId('page-node-icon')
    expect(icon.tagName.toLowerCase()).toBe('svg')
    expect(icon.getAttribute('class') ?? '').toContain('lucide-shopping-cart')
  })

  it('falls back to HelpCircle for an unknown name without throwing', () => {
    expect(() =>
      renderIcon({ id: 'ic3', type: 'icon', props: { name: 'not-a-real-icon-xyz' } })
    ).not.toThrow()
    const icon = screen.getByTestId('page-node-icon')
    expect(icon.tagName.toLowerCase()).toBe('svg')
    // HelpCircle (the fallback) renders with the lucide help-circle class.
    expect(icon.getAttribute('class') ?? '').toContain('lucide-circle-question-mark')
  })
})
