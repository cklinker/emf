import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from '../../../context/I18nContext'
import { ReportViewPage } from './ReportViewPage'

const mockPost = vi.fn()
const mockGet = vi.fn()
const mockGetBlob = vi.fn()

vi.mock('../../../context/ApiContext', () => ({
  useApi: vi.fn(() => ({
    apiClient: {
      post: (...a: unknown[]) => mockPost(...a),
      get: (...a: unknown[]) => mockGet(...a),
      getBlob: (...a: unknown[]) => mockGetBlob(...a),
    },
  })),
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <I18nProvider>
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/acme/app/reports/rep-1']}>
          <Routes>
            <Route path="/:tenantSlug/app/reports/:id" element={<ReportViewPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nProvider>
  )
}

const RESULT = {
  data: {
    attributes: {
      reportId: 'rep-1',
      reportName: 'Pipeline by Stage',
      reportType: 'TABULAR',
      columns: [
        { fieldName: 'name', label: 'Name', type: 'string' },
        { fieldName: 'total', label: 'Total', type: 'number' },
      ],
      records: [
        { name: 'Acme', total: 100 },
        { name: 'Beta', total: 50 },
      ],
    },
  },
  meta: { totalCount: 2, currentPage: 1, pageSize: 100, totalPages: 1 },
}

beforeEach(() => {
  vi.clearAllMocks()
  mockPost.mockResolvedValue(RESULT)
})

describe('ReportViewPage', () => {
  it('executes the report and renders columns, rows, and meta', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Pipeline by Stage')).toBeTruthy())
    expect(mockPost).toHaveBeenCalledWith(
      '/api/reports/rep-1/execute?page[number]=1&page[size]=100',
      {}
    )
    expect(screen.getAllByTestId('report-row')).toHaveLength(2)
    expect(screen.getByTestId('report-meta').textContent).toContain('2')
    expect((screen.getByTestId('report-prev') as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByTestId('report-next') as HTMLButtonElement).disabled).toBe(true)
  })

  it('exports CSV via get and PDF via getBlob', async () => {
    mockGet.mockResolvedValue('a,b\n1,2')
    mockGetBlob.mockResolvedValue(new Blob(['pdf']))
    renderPage()
    await waitFor(() => expect(screen.getByTestId('export-csv')).toBeTruthy())

    fireEvent.click(screen.getByTestId('export-csv'))
    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith('/api/reports/rep-1/export?format=csv')
    )
    fireEvent.click(screen.getByTestId('export-pdf'))
    await waitFor(() =>
      expect(mockGetBlob).toHaveBeenCalledWith('/api/reports/rep-1/export?format=pdf')
    )
  })

  it('surfaces the server validation detail verbatim on a 400', async () => {
    mockPost.mockRejectedValue({
      response: {
        data: { errors: [{ detail: 'Cannot group on a masked field: ssn' }] },
      },
    })
    renderPage()
    await waitFor(() =>
      expect(screen.getByTestId('report-error').textContent).toContain(
        'Cannot group on a masked field: ssn'
      )
    )
  })
})
