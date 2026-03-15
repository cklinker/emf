import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { BrowserRouter } from 'react-router-dom'
import { I18nProvider } from '../../context/I18nContext'
import { AnalyticsPage } from './AnalyticsPage'

// Mock useApi directly to bypass KeltaClient initialization
const mockListDashboards = vi.fn()
const mockGetGuestToken = vi.fn()

vi.mock('../../context/ApiContext', () => ({
  ApiProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useApi: () => ({
    keltaClient: {
      admin: {
        superset: {
          listDashboards: mockListDashboards,
          getGuestToken: mockGetGuestToken,
        },
      },
    },
  }),
}))

function createSimpleWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <I18nProvider>{children}</I18nProvider>
        </BrowserRouter>
      </QueryClientProvider>
    )
  }
}

describe('AnalyticsPage', () => {
  beforeEach(() => {
    mockListDashboards.mockReset()
    mockGetGuestToken.mockReset()
  })

  it('renders the page title', async () => {
    mockListDashboards.mockResolvedValue([])

    render(<AnalyticsPage />, { wrapper: createSimpleWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Analytics')).toBeInTheDocument()
    })
  })

  it('shows empty state when no dashboards exist', async () => {
    mockListDashboards.mockResolvedValue([])

    render(<AnalyticsPage />, { wrapper: createSimpleWrapper() })

    await waitFor(() => {
      expect(screen.getByText('No dashboards available')).toBeInTheDocument()
    })
  })

  it('renders dashboard cards when dashboards exist', async () => {
    mockListDashboards.mockResolvedValue([
      {
        id: 1,
        dashboard_title: 'Sales Overview',
        url: '/superset/dashboard/1/',
        status: 'published',
        published: true,
        changed_on_utc: '2024-06-15T10:00:00Z',
      },
      {
        id: 2,
        dashboard_title: 'Customer Metrics',
        url: '/superset/dashboard/2/',
        status: 'draft',
        published: false,
        changed_on_utc: '2024-06-14T08:00:00Z',
      },
    ])

    render(<AnalyticsPage />, { wrapper: createSimpleWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Sales Overview')).toBeInTheDocument()
      expect(screen.getByText('Customer Metrics')).toBeInTheDocument()
    })
  })

  it('shows published/draft badge for dashboards', async () => {
    mockListDashboards.mockResolvedValue([
      {
        id: 1,
        dashboard_title: 'Published Dashboard',
        status: 'published',
        published: true,
        changed_on_utc: '2024-06-15T10:00:00Z',
      },
    ])

    render(<AnalyticsPage />, { wrapper: createSimpleWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Published')).toBeInTheDocument()
    })
  })

  it('has a link to open Superset directly', async () => {
    mockListDashboards.mockResolvedValue([])

    render(<AnalyticsPage />, { wrapper: createSimpleWrapper() })

    await waitFor(() => {
      const link = screen.getByText('Open in Superset')
      expect(link.closest('a')).toHaveAttribute('target', '_blank')
    })
  })

  it('shows error state when API call fails', async () => {
    mockListDashboards.mockRejectedValue(new Error('Network error'))

    render(<AnalyticsPage />, { wrapper: createSimpleWrapper() })

    await waitFor(() => {
      expect(screen.getByText(/Failed to load dashboards/)).toBeInTheDocument()
    })
  })
})
