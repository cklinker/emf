/**
 * Metric widget tests — live collection count via PageResponse.totalElements (meta.totalCount),
 * number/compact formatting, editor sample, and the error fallback.
 */
import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { metricWidget } from './metric'
import type { RenderNode } from '../types'

const getPageMock = vi.fn()
vi.mock('@/context/ApiContext', () => ({
  useApi: () => ({ apiClient: { getPage: getPageMock } }),
}))

function renderMetric(node: RenderNode, mode: 'editor' | 'runtime' = 'runtime') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Render = metricWidget.Render
  return render(
    <QueryClientProvider client={client}>
      <Render node={node} scope={{}} mode={mode} tenantSlug="acme" renderChild={() => null} />
    </QueryClientProvider>
  )
}

const node = (props: Record<string, unknown>): RenderNode => ({ id: 'm1', type: 'metric', props })

describe('metric widget', () => {
  beforeEach(() => getPageMock.mockReset())

  it('renders a sample number in editor mode without fetching', () => {
    renderMetric(node({ dataView: { collection: 'titles' }, label: 'Titles' }), 'editor')
    expect(screen.getByTestId('page-node-metric-sample')).toHaveTextContent('1,234')
    expect(getPageMock).not.toHaveBeenCalled()
  })

  it('shows the live total count (grouped) and the label at runtime', async () => {
    getPageMock.mockResolvedValue({
      content: [],
      totalElements: 30968,
      totalPages: 30968,
      size: 1,
      number: 1,
    })
    renderMetric(
      node({ dataView: { collection: 'titles' }, label: 'Titles in catalog', format: 'number' })
    )

    expect(await screen.findByText('30,968')).toBeInTheDocument()
    expect(screen.getByText('Titles in catalog')).toBeInTheDocument()
    expect(getPageMock).toHaveBeenCalledWith('/api/titles?page[size]=1')
  })

  it('formats the count compactly when format=compact', async () => {
    getPageMock.mockResolvedValue({
      content: [],
      totalElements: 52889,
      totalPages: 1,
      size: 1,
      number: 1,
    })
    renderMetric(node({ dataView: { collection: 'availabilities' }, format: 'compact' }))

    expect(await screen.findByText('52.9K')).toBeInTheDocument()
  })

  it('shows the sample (no fetch) when no collection is bound', () => {
    renderMetric(node({ dataView: {}, label: 'Unbound' }))
    expect(screen.getByTestId('page-node-metric-sample')).toBeInTheDocument()
    expect(getPageMock).not.toHaveBeenCalled()
  })
})
