/**
 * Tabs widget (slice 2g). Renders the radix tab list from `props.tabs[]`, renders each tab's matching
 * `tab-panel.children` sub-tree through the shared `renderChild`, and switches the visible sub-tree on
 * trigger click. Empty `props.tabs[]` → the i18n empty state.
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider } from '@/context/I18nContext'
import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import type { RenderNode } from '../types'

function renderTabs(node: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  return render(
    <I18nProvider>
      <RenderTree components={[node]} tenantSlug="acme" mode={mode} />
    </I18nProvider>
  )
}

const TABS_NODE: RenderNode = {
  id: 'tb1',
  type: 'tabs',
  props: {
    tabs: [
      { value: 'overview', label: 'Overview' },
      { value: 'details', label: 'Details' },
    ],
    defaultTab: 'overview',
  },
  children: [
    {
      id: 'panel-overview',
      type: 'tab-panel',
      props: { value: 'overview' },
      children: [{ id: 'h1', type: 'heading', props: { text: 'Orders', level: 'h2' } }],
    },
    {
      id: 'panel-details',
      type: 'tab-panel',
      props: { value: 'details' },
      children: [{ id: 't1', type: 'text', props: { content: 'Detail content.' } }],
    },
  ],
}

beforeEach(() => {
  registerBuiltinWidgets()
})

describe('tabs widget', () => {
  it('renders the tab list from props.tabs[]', () => {
    renderTabs(TABS_NODE)
    expect(screen.getByTestId('page-node-tabs')).toBeInTheDocument()
    expect(screen.getByTestId('tab-trigger-overview')).toHaveTextContent('Overview')
    expect(screen.getByTestId('tab-trigger-details')).toHaveTextContent('Details')
  })

  it('renders the active tab children sub-tree via renderChild', () => {
    renderTabs(TABS_NODE)
    // Default tab "overview" content is visible.
    const overviewContent = screen.getByTestId('tab-content-overview')
    expect(overviewContent).toHaveTextContent('Orders')
    expect(screen.getByTestId('page-node-heading')).toHaveTextContent('Orders')
  })

  it('switches the rendered sub-tree on trigger click', async () => {
    renderTabs(TABS_NODE)
    // Radix hides the inactive panel via data-[state=inactive]:hidden; switch to "details".
    await userEvent.click(screen.getByTestId('tab-trigger-details'))
    const detailsContent = await screen.findByTestId('tab-content-details')
    expect(detailsContent).toHaveTextContent('Detail content.')
    expect(screen.getByTestId('page-node-text')).toHaveTextContent('Detail content.')
  })

  it('shows the empty state when no tabs are configured', () => {
    renderTabs({ id: 'tb-empty', type: 'tabs', props: { tabs: [] } })
    expect(screen.getByTestId('page-node-tabs-empty')).toHaveTextContent('No tabs configured.')
  })
})
