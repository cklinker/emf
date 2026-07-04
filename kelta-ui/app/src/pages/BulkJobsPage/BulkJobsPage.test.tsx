/**
 * BulkJobsPage Tests
 *
 * Tests for the BulkJobsPage component including:
 * - Jobs list rendering
 * - Creating a bulk job from inline records (JSON path)
 * - Creating a bulk job from a file upload (multipart path)
 * - Downloading results for completed/failed jobs
 */

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'
import { BulkJobsPage } from './BulkJobsPage'

// Mock URL.createObjectURL and revokeObjectURL for the download flow
global.URL.createObjectURL = vi.fn(() => 'blob:mock-url')
global.URL.revokeObjectURL = vi.fn()

interface MockJob {
  id: string
  collectionId: string
  operation: string
  status: string
  totalRecords: number
  processedRecords: number
  successRecords: number
  errorRecords: number
  externalIdField: string | null
  batchSize: number
  createdBy: string
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

function makeJob(overrides: Partial<MockJob> = {}): MockJob {
  return {
    id: 'job-1',
    collectionId: 'col-1',
    operation: 'INSERT',
    status: 'COMPLETED',
    totalRecords: 10,
    processedRecords: 10,
    successRecords: 9,
    errorRecords: 1,
    externalIdField: null,
    batchSize: 200,
    createdBy: 'user-1',
    startedAt: '2026-07-01T10:00:00Z',
    completedAt: '2026-07-01T10:05:00Z',
    createdAt: '2026-07-01T09:59:00Z',
    updatedAt: '2026-07-01T10:05:00Z',
    ...overrides,
  }
}

/**
 * Set up mockAxios.get so the jobs list query resolves with the given jobs.
 * unwrapJsonApiList falls back to plain arrays, so `{ data: [...] }` works.
 */
function setupJobsListMock(jobs: MockJob[], downloadCsv = 'id,status\nr1,SUCCESS\n') {
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes('/results/download')) {
      return Promise.resolve({ data: downloadCsv })
    }
    if (url === '/api/bulk-jobs') {
      return Promise.resolve({ data: jobs })
    }
    return Promise.resolve({ data: {} })
  })
}

describe('BulkJobsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  describe('Rendering', () => {
    it('renders the jobs table with jobs from the API', async () => {
      setupJobsListMock([makeJob()])

      render(<BulkJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('bulk-jobs-table')).toBeInTheDocument()
      })
      expect(screen.getByText('col-1')).toBeInTheDocument()
    })

    it('renders empty state when there are no jobs', async () => {
      setupJobsListMock([])

      render(<BulkJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('empty-state')).toBeInTheDocument()
      })
    })
  })

  describe('Create form - file upload mode', () => {
    it('posts multipart FormData to /api/bulk-jobs/upload when a file is selected', async () => {
      const user = userEvent.setup()
      setupJobsListMock([])
      mockAxios.post.mockResolvedValue({
        data: { data: { type: 'bulk-jobs', id: 'job-new', attributes: {} } },
      })

      render(<BulkJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-bulk-job-button')).toBeInTheDocument()
      })
      await user.click(screen.getByTestId('add-bulk-job-button'))

      await user.type(screen.getByTestId('bulk-job-collection-id-input'), 'accounts')
      await user.selectOptions(screen.getByTestId('bulk-job-operation-input'), 'UPSERT')
      await user.type(screen.getByTestId('bulk-job-external-id-input'), 'external_ref')

      const file = new File(['name\nAcme\n'], 'records.csv', { type: 'text/csv' })
      await user.upload(screen.getByTestId('bulk-job-file-input'), file)
      expect(screen.getByTestId('bulk-job-file-name')).toHaveTextContent('records.csv')

      await user.click(screen.getByTestId('bulk-job-form-submit'))

      await waitFor(() => {
        expect(mockAxios.post).toHaveBeenCalledTimes(1)
      })

      const [url, body] = mockAxios.post.mock.calls[0] as [string, FormData]
      expect(url).toBe('/api/bulk-jobs/upload')
      expect(body).toBeInstanceOf(FormData)
      expect(body.get('collectionId')).toBe('accounts')
      expect(body.get('operation')).toBe('UPSERT')
      expect(body.get('externalIdField')).toBe('external_ref')
      expect(body.get('batchSize')).toBe('200')
      const uploaded = body.get('file')
      expect(uploaded).toBeInstanceOf(File)
      expect((uploaded as File).name).toBe('records.csv')
    })

    it('clears the selected file when Remove is clicked', async () => {
      const user = userEvent.setup()
      setupJobsListMock([])

      render(<BulkJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-bulk-job-button')).toBeInTheDocument()
      })
      await user.click(screen.getByTestId('add-bulk-job-button'))

      const file = new File(['{}'], 'records.json', { type: 'application/json' })
      await user.upload(screen.getByTestId('bulk-job-file-input'), file)
      expect(screen.getByTestId('bulk-job-file-name')).toHaveTextContent('records.json')

      await user.click(screen.getByTestId('bulk-job-file-remove'))
      expect(screen.queryByTestId('bulk-job-file-name')).not.toBeInTheDocument()
    })

    it('keeps the JSON create path when no file is selected', async () => {
      const user = userEvent.setup()
      setupJobsListMock([])
      mockAxios.post.mockResolvedValue({
        data: { data: { type: 'bulk-jobs', id: 'job-new', attributes: {} } },
      })

      render(<BulkJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-bulk-job-button')).toBeInTheDocument()
      })
      await user.click(screen.getByTestId('add-bulk-job-button'))

      await user.type(screen.getByTestId('bulk-job-collection-id-input'), 'accounts')
      await user.click(screen.getByTestId('bulk-job-form-submit'))

      await waitFor(() => {
        expect(mockAxios.post).toHaveBeenCalledTimes(1)
      })

      const [url, body] = mockAxios.post.mock.calls[0] as [string, unknown]
      expect(url).toBe('/api/bulk-jobs')
      expect(body).not.toBeInstanceOf(FormData)
    })
  })

  describe('Download results', () => {
    it('shows a download button for completed jobs and fetches the results CSV', async () => {
      const user = userEvent.setup()
      setupJobsListMock([makeJob({ id: 'job-1', status: 'COMPLETED' })])

      render(<BulkJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('download-results-button-0')).toBeInTheDocument()
      })

      await user.click(screen.getByTestId('download-results-button-0'))

      await waitFor(() => {
        expect(mockAxios.get).toHaveBeenCalledWith('/api/bulk-jobs/job-1/results/download')
      })
      expect(global.URL.createObjectURL).toHaveBeenCalled()
    })

    it('shows a download button for failed jobs with processed records', async () => {
      setupJobsListMock([
        makeJob({ id: 'job-2', status: 'FAILED', processedRecords: 5, successRecords: 3 }),
      ])

      render(<BulkJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('download-results-button-0')).toBeInTheDocument()
      })
    })

    it('does not show a download button for queued or unprocessed failed jobs', async () => {
      setupJobsListMock([
        makeJob({ id: 'job-3', status: 'QUEUED', processedRecords: 0 }),
        makeJob({ id: 'job-4', status: 'FAILED', processedRecords: 0 }),
      ])

      render(<BulkJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('bulk-jobs-table')).toBeInTheDocument()
      })
      expect(screen.queryByTestId('download-results-button-0')).not.toBeInTheDocument()
      expect(screen.queryByTestId('download-results-button-1')).not.toBeInTheDocument()
    })
  })
})
