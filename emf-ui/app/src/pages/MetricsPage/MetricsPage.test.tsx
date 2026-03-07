import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MetricsPage } from './MetricsPage'

// Mock recharts to avoid canvas/SVG issues in test
vi.mock('recharts', () => ({
  LineChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="line-chart">{children}</div>
  ),
  Line: () => null,
  AreaChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="area-chart">{children}</div>
  ),
  Area: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  Legend: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

const mockSummary = {
  totalRequests: 15230,
  errorRate: 1.25,
  avgLatencyMs: 142.5,
  activeRequests: 8,
}

const mockQueryResult = {
  metric: 'requests',
  start: '2024-01-01T00:00:00Z',
  end: '2024-01-01T01:00:00Z',
  step: '60s',
  series: [
    {
      labels: { status: '200' },
      dataPoints: [
        { timestamp: 1704067200, value: 42.5 },
        { timestamp: 1704067260, value: 38.1 },
      ],
    },
  ],
}

function toJsonApiSingleResource(type: string, id: string, attrs: Record<string, unknown>) {
  return {
    data: {
      data: {
        type,
        id,
        attributes: { ...attrs },
      },
    },
  }
}

function setupAxiosMocks() {
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes('/api/metrics/summary')) {
      return Promise.resolve(toJsonApiSingleResource('metrics-summary', 'current', mockSummary))
    }
    if (url.includes('/api/metrics/query')) {
      return Promise.resolve(toJsonApiSingleResource('metrics-query', 'requests', mockQueryResult))
    }
    return Promise.resolve({ data: {} })
  })
}

describe('MetricsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
    setupAxiosMocks()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  describe('Rendering', () => {
    it('renders the page with title', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Metrics')).toBeInTheDocument()
      })
    })

    it('shows loading state initially', () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })
      expect(screen.getByTestId('loading-spinner')).toBeInTheDocument()
    })

    it('renders the metrics-page container', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-page')).toBeInTheDocument()
      })
    })
  })

  describe('Summary cards', () => {
    it('shows all four summary cards', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-summary-cards')).toBeInTheDocument()
      })

      expect(screen.getByTestId('metrics-summary-card-requests')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-summary-card-error-rate')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-summary-card-latency')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-summary-card-active')).toBeInTheDocument()
    })

    it('displays summary values', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('15,230')).toBeInTheDocument()
      })

      expect(screen.getByText('1.25%')).toBeInTheDocument()
      expect(screen.getByText('142.5 ms')).toBeInTheDocument()
      expect(screen.getByText('8')).toBeInTheDocument()
    })
  })

  describe('Time range toolbar', () => {
    it('shows time range selector', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-time-range')).toBeInTheDocument()
      })
    })

    it('shows all time range buttons', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-time-range-1h')).toBeInTheDocument()
      })

      expect(screen.getByTestId('metrics-time-range-6h')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-time-range-24h')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-time-range-7d')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-time-range-30d')).toBeInTheDocument()
    })

    it('can change time range', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-time-range-7d')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('metrics-time-range-7d'))

      // The button should now have the active styling (primary background)
      await waitFor(() => {
        expect(screen.getByTestId('metrics-time-range-7d')).toHaveClass('bg-primary')
      })
    })
  })

  describe('Route filter', () => {
    it('shows route filter dropdown', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-route-filter')).toBeInTheDocument()
      })
    })

    it('has "All Routes" as default option', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-route-filter')).toBeInTheDocument()
      })

      const select = screen.getByTestId('metrics-route-filter') as HTMLSelectElement
      expect(select.value).toBe('')
    })
  })

  describe('Charts', () => {
    it('renders chart grid', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-chart-grid')).toBeInTheDocument()
      })
    })

    it('renders all six chart panels', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-chart-request-rate')).toBeInTheDocument()
      })

      expect(screen.getByTestId('metrics-chart-latency')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-chart-errors')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-chart-auth-failures')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-chart-rate-limit')).toBeInTheDocument()
      expect(screen.getByTestId('metrics-chart-active-requests')).toBeInTheDocument()
    })

    it('charts have proper titles', async () => {
      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Request Rate')).toBeInTheDocument()
      })

      expect(screen.getByText('Latency')).toBeInTheDocument()
      expect(screen.getByText('Errors')).toBeInTheDocument()
      expect(screen.getByText('Auth Failures')).toBeInTheDocument()
      expect(screen.getByText('Rate Limit Events')).toBeInTheDocument()
      // "Active Requests" appears in both summary card and chart, so query within chart grid
      const chartGrid = screen.getByTestId('metrics-chart-grid')
      expect(
        chartGrid.querySelector('[data-testid="metrics-chart-active-requests"]')
      ).toBeInTheDocument()
    })
  })

  describe('Error handling', () => {
    it('shows error message on fetch failure', async () => {
      mockAxios.get.mockImplementation(() =>
        Promise.reject({ isAxiosError: true, response: { status: 500 }, message: 'fail' })
      )

      render(<MetricsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByText('Failed to load metrics.')).toBeInTheDocument()
      })
    })
  })
})
