/**
 * Shared render-path test for the 2g breadth widgets (chart/tabs/nav/icon/link/image). Asserts each
 * resolves through the SAME descriptor `Render` (no unknown fallback) in BOTH `mode:'editor'` and
 * `mode:'runtime'`, confirming the editor-preview/runtime de-dup holds for the breadth set.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { I18nProvider } from '@/context/I18nContext'

vi.mock('recharts', async () => {
  const actual = await vi.importActual<typeof import('recharts')>('recharts')
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: ReactNode }) => (
      <div style={{ width: 400, height: 300 }}>{children}</div>
    ),
  }
})

import { RenderTree } from './renderTree'
import { registerBuiltinWidgets } from './builtins'
import type { RenderNode } from './types'

const TREE: RenderNode[] = [
  {
    id: 'ch',
    type: 'chart',
    props: { chartType: 'bar', xKey: 'm', series: [{ key: 'v' }], dataView: [{ m: 'A', v: 1 }] },
  },
  {
    id: 'tb',
    type: 'tabs',
    props: { tabs: [{ value: 'a', label: 'A' }], defaultTab: 'a' },
    children: [
      {
        id: 'pa',
        type: 'tab-panel',
        props: { value: 'a' },
        children: [{ id: 'h', type: 'heading', props: { text: 'In tab' } }],
      },
    ],
  },
  { id: 'nv', type: 'nav', props: { items: [{ id: 'h', label: 'Home', path: '/' }] } },
  { id: 'ic', type: 'icon', props: { name: 'star' } },
  { id: 'lk', type: 'link', props: { label: 'Docs', href: 'https://kelta.io' } },
  { id: 'im', type: 'image', props: { src: 'https://cdn.example.com/a.png', alt: 'A' } },
]

function renderTree(mode: 'editor' | 'runtime') {
  return render(
    <I18nProvider>
      <MemoryRouter>
        <RenderTree components={TREE} tenantSlug="acme" mode={mode} />
      </MemoryRouter>
    </I18nProvider>
  )
}

beforeEach(() => {
  registerBuiltinWidgets()
})

describe('2g breadth widgets resolve through the shared render path', () => {
  it.each(['editor', 'runtime'] as const)(
    'renders every breadth widget through its descriptor (mode: %s)',
    (mode) => {
      renderTree(mode)
      expect(screen.queryByTestId('page-node-unknown')).not.toBeInTheDocument()
      expect(screen.getByTestId('page-node-chart')).toBeInTheDocument()
      expect(screen.getByTestId('page-node-tabs')).toBeInTheDocument()
      expect(screen.getByTestId('page-node-nav')).toBeInTheDocument()
      expect(screen.getByTestId('page-node-icon')).toBeInTheDocument()
      expect(screen.getByTestId('page-node-link')).toBeInTheDocument()
      expect(screen.getByTestId('page-node-image')).toBeInTheDocument()
    }
  )
})
