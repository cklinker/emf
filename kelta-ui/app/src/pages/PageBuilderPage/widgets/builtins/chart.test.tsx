/**
 * Chart widget (slice 2g). Renders FROM the descriptor through `RenderTree` (single render path), binds a
 * resolved data array, switches type, and degrades to the editor sample / runtime "No data" empty state.
 *
 * `recharts`' `ResponsiveContainer` needs a measured parent box (jsdom has none), so we mock it to a
 * fixed-size passthrough — the standard pattern for charting recharts under jsdom.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
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

import { RenderTree } from '../renderTree'
import { registerBuiltinWidgets } from './index'
import type { RenderNode } from '../types'

function renderChart(node: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  return render(
    <I18nProvider>
      <RenderTree components={[node]} tenantSlug="acme" mode={mode} />
    </I18nProvider>
  )
}

const DATA = [
  { month: 'Jan', total: 10, count: 2 },
  { month: 'Feb', total: 20, count: 5 },
  { month: 'Mar', total: 15, count: 4 },
]

beforeEach(() => {
  registerBuiltinWidgets()
})

describe('chart widget', () => {
  it('renders from the descriptor and binds a resolved data array (bar)', () => {
    renderChart({
      id: 'c1',
      type: 'chart',
      props: { chartType: 'bar', xKey: 'month', series: [{ key: 'total' }], dataView: DATA },
    })
    const chart = screen.getByTestId('page-node-chart')
    expect(chart).toBeInTheDocument()
    expect(chart).toHaveAttribute('data-chart-type', 'bar')
  })

  it('honors chartType line and pie', () => {
    renderChart({
      id: 'c2',
      type: 'chart',
      props: { chartType: 'line', xKey: 'month', series: [{ key: 'total' }], dataView: DATA },
    })
    expect(screen.getByTestId('page-node-chart')).toHaveAttribute('data-chart-type', 'line')

    renderChart({
      id: 'c3',
      type: 'chart',
      props: { chartType: 'pie', xKey: 'month', series: [{ key: 'total' }], dataView: DATA },
    })
    expect(screen.getAllByTestId('page-node-chart')[1]).toHaveAttribute('data-chart-type', 'pie')
  })

  it('shows the editor sample (not "No data") when no dataView is set in editor mode', () => {
    renderChart({ id: 'c4', type: 'chart', props: { chartType: 'bar' } }, 'editor')
    expect(screen.getByTestId('page-node-chart')).toBeInTheDocument()
    expect(screen.queryByTestId('page-node-chart-empty')).not.toBeInTheDocument()
  })

  it('shows the "No data" empty state in runtime mode with no data', () => {
    renderChart({ id: 'c5', type: 'chart', props: { chartType: 'bar' } }, 'runtime')
    expect(screen.getByTestId('page-node-chart-empty')).toHaveTextContent('No data')
  })

  it('treats an unresolved Binding object as empty (degrade-safe)', () => {
    renderChart(
      {
        id: 'c6',
        type: 'chart',
        props: { chartType: 'bar', dataView: { $bind: 'data.orders', mode: 'path' } },
      },
      'runtime'
    )
    expect(screen.getByTestId('page-node-chart-empty')).toBeInTheDocument()
  })
})
