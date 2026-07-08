import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from '../../../context/I18nContext'
import { AnalyticsHubPage } from './AnalyticsHubPage'

const mockGetList = vi.fn()
vi.mock('../../../context/ApiContext', () => ({
  useApi: vi.fn(() => ({ apiClient: { getList: (...a: unknown[]) => mockGetList(...a) } })),
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <I18nProvider>
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/acme/app/analytics']}>
          <Routes>
            <Route path="/:tenantSlug/app/analytics" element={<AnalyticsHubPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nProvider>
  )
}

beforeEach(() => vi.clearAllMocks())

describe('AnalyticsHubPage', () => {
  it('lists dashboards and reports with viewer links', async () => {
    mockGetList.mockImplementation((url: string) =>
      url.includes('/api/dashboards')
        ? Promise.resolve([{ id: 'd1', name: 'Sales', description: null }])
        : Promise.resolve([
            { id: 'r1', name: 'Pipeline', description: null, reportType: 'TABULAR' },
          ])
    )
    renderPage()

    await waitFor(() => expect(screen.getByTestId('hub-dashboard-card')).toBeTruthy())
    expect(screen.getByTestId('hub-dashboard-card').getAttribute('href')).toBe(
      '/acme/app/dashboards/d1'
    )
    expect(screen.getByTestId('hub-report-run').getAttribute('href')).toBe('/acme/app/reports/r1')
  })

  it('renders the empty state when both lists are empty', async () => {
    mockGetList.mockResolvedValue([])
    renderPage()
    await waitFor(() => expect(screen.getByTestId('hub-empty')).toBeTruthy())
  })
})
