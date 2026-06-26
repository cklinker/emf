/**
 * Nav widget (slice 2g). Renders `@kelta/components`' `Navigation` from `props.items[]`, passes through
 * `orientation`, and shows the i18n empty state when there are no items. `Navigation` uses
 * `react-router-dom`, so render under a `MemoryRouter`.
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { I18nProvider } from '@/context/I18nContext'
import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import type { RenderNode } from '../types'

function renderNav(node: RenderNode) {
  return render(
    <I18nProvider>
      <MemoryRouter>
        <RenderTree components={[node]} tenantSlug="acme" mode="runtime" />
      </MemoryRouter>
    </I18nProvider>
  )
}

beforeEach(() => {
  registerBuiltinWidgets()
})

describe('nav widget', () => {
  it('renders Navigation from props.items[] with item labels', () => {
    renderNav({
      id: 'nv1',
      type: 'nav',
      props: {
        orientation: 'horizontal',
        items: [
          { id: 'home', label: 'Home', path: '/app/p/home' },
          { id: 'orders', label: 'Orders', path: '/app/p/orders' },
        ],
      },
    })
    const nav = screen.getByTestId('page-node-nav')
    expect(nav).toBeInTheDocument()
    expect(nav).toHaveTextContent('Home')
    expect(nav).toHaveTextContent('Orders')
  })

  it('passes orientation through to Navigation', () => {
    renderNav({
      id: 'nv2',
      type: 'nav',
      props: {
        orientation: 'vertical',
        items: [{ id: 'home', label: 'Home', path: '/' }],
      },
    })
    expect(screen.getByTestId('page-node-nav')).toHaveClass('kelta-navigation--vertical')
  })

  it('shows the empty state with no items', () => {
    renderNav({ id: 'nv3', type: 'nav', props: { items: [] } })
    expect(screen.getByTestId('page-node-nav-empty')).toHaveTextContent('No menu items.')
  })
})
