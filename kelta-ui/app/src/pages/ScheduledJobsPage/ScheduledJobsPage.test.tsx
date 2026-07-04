/**
 * ScheduledJobsPage Tests
 *
 * Tests for the ScheduledJobsPage component including:
 * - Jobs list rendering
 * - Email recipients config for REPORT_EXPORT jobs
 *   - textarea visibility per job type
 *   - parsing/de-duplication and config.recipients submission
 *   - invalid email validation blocking submit
 *   - pre-fill on edit + recipients badge in the table
 */

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'
import { ScheduledJobsPage } from './ScheduledJobsPage'

interface MockScheduledJob {
  id: string
  name: string
  description: string | null
  jobType: string
  jobReferenceId: string | null
  cronExpression: string
  timezone: string
  active: boolean
  config?: { recipients?: string[] } | null
  lastRun: string | null
  lastStatus: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

function makeJob(overrides: Partial<MockScheduledJob> = {}): MockScheduledJob {
  return {
    id: 'sj-1',
    name: 'Nightly export',
    description: null,
    jobType: 'FLOW',
    jobReferenceId: 'ref-1',
    cronExpression: '0 0 * * *',
    timezone: 'UTC',
    active: true,
    config: null,
    lastRun: null,
    lastStatus: null,
    createdBy: 'user-1',
    createdAt: '2026-07-01T09:00:00Z',
    updatedAt: '2026-07-01T09:00:00Z',
    ...overrides,
  }
}

/**
 * Set up mockAxios.get so the jobs list query resolves with the given jobs.
 * unwrapJsonApiList falls back to plain arrays, so `{ data: [...] }` works.
 */
function setupJobsListMock(jobs: MockScheduledJob[]) {
  mockAxios.get.mockImplementation((url: string) => {
    if (url === '/api/scheduled-jobs') {
      return Promise.resolve({ data: jobs })
    }
    return Promise.resolve({ data: [] })
  })
}

interface JsonApiBody {
  data: { type: string; id?: string; attributes: Record<string, unknown> }
}

async function openCreateForm(user: ReturnType<typeof userEvent.setup>) {
  await waitFor(() => {
    expect(screen.getByTestId('add-scheduled-job-button')).toBeInTheDocument()
  })
  await user.click(screen.getByTestId('add-scheduled-job-button'))
  await user.type(screen.getByTestId('scheduled-job-name-input'), 'Weekly report')
  await user.type(screen.getByTestId('scheduled-job-cron-expression-input'), '0 6 * * 1')
}

describe('ScheduledJobsPage', () => {
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

      render(<ScheduledJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('scheduled-jobs-table')).toBeInTheDocument()
      })
      expect(screen.getByText('Nightly export')).toBeInTheDocument()
    })

    it('shows a recipients badge for REPORT_EXPORT jobs with recipients', async () => {
      setupJobsListMock([
        makeJob({
          id: 'sj-2',
          jobType: 'REPORT_EXPORT',
          config: { recipients: ['a@x.com', 'b@y.com'] },
        }),
        makeJob({ id: 'sj-3', jobType: 'REPORT_EXPORT', config: null }),
        makeJob({ id: 'sj-4', jobType: 'FLOW' }),
      ])

      render(<ScheduledJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('recipients-badge-0')).toBeInTheDocument()
      })
      expect(screen.getByTestId('recipients-badge-0')).toHaveTextContent('2 recipients')
      expect(screen.queryByTestId('recipients-badge-1')).not.toBeInTheDocument()
      expect(screen.queryByTestId('recipients-badge-2')).not.toBeInTheDocument()
    })
  })

  describe('Email recipients field', () => {
    it('shows the recipients textarea only for REPORT_EXPORT job type', async () => {
      const user = userEvent.setup()
      setupJobsListMock([])

      render(<ScheduledJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-scheduled-job-button')).toBeInTheDocument()
      })
      await user.click(screen.getByTestId('add-scheduled-job-button'))

      // Default job type is FLOW - no recipients textarea
      expect(screen.queryByTestId('scheduled-job-recipients-input')).not.toBeInTheDocument()

      await user.selectOptions(screen.getByTestId('scheduled-job-type-input'), 'REPORT_EXPORT')
      expect(screen.getByTestId('scheduled-job-recipients-input')).toBeInTheDocument()
      expect(
        screen.getByText(
          'Report CSV is emailed to these addresses after each run; leave empty to skip delivery'
        )
      ).toBeInTheDocument()

      await user.selectOptions(screen.getByTestId('scheduled-job-type-input'), 'SCRIPT')
      expect(screen.queryByTestId('scheduled-job-recipients-input')).not.toBeInTheDocument()
    })

    it('submits config.recipients as a trimmed, de-duplicated array', async () => {
      const user = userEvent.setup()
      setupJobsListMock([])
      mockAxios.post.mockResolvedValue({
        data: { data: { type: 'scheduled-jobs', id: 'sj-new', attributes: {} } },
      })

      render(<ScheduledJobsPage />, { wrapper: createTestWrapper() })

      await openCreateForm(user)
      await user.selectOptions(screen.getByTestId('scheduled-job-type-input'), 'REPORT_EXPORT')
      await user.type(
        screen.getByTestId('scheduled-job-recipients-input'),
        ' a@x.com , b@y.com,a@x.com, '
      )
      await user.click(screen.getByTestId('scheduled-job-form-submit'))

      await waitFor(() => {
        expect(mockAxios.post).toHaveBeenCalledTimes(1)
      })

      const [url, body] = mockAxios.post.mock.calls[0] as [string, JsonApiBody]
      expect(url).toBe('/api/scheduled-jobs')
      expect(body.data.type).toBe('scheduled-jobs')
      expect(body.data.attributes.config).toEqual({ recipients: ['a@x.com', 'b@y.com'] })
      expect(body.data.attributes).not.toHaveProperty('recipientsText')
    })

    it('sends an explicit empty recipients list so edits can clear saved delivery config', async () => {
      const user = userEvent.setup()
      setupJobsListMock([])
      mockAxios.post.mockResolvedValue({
        data: { data: { type: 'scheduled-jobs', id: 'sj-new', attributes: {} } },
      })

      render(<ScheduledJobsPage />, { wrapper: createTestWrapper() })

      await openCreateForm(user)
      await user.selectOptions(screen.getByTestId('scheduled-job-type-input'), 'REPORT_EXPORT')
      await user.click(screen.getByTestId('scheduled-job-form-submit'))

      await waitFor(() => {
        expect(mockAxios.post).toHaveBeenCalledTimes(1)
      })

      const [, body] = mockAxios.post.mock.calls[0] as [string, JsonApiBody]
      expect(body.data.attributes).toHaveProperty('config', { recipients: [] })
    })

    it('blocks submit and shows an inline error listing invalid emails', async () => {
      const user = userEvent.setup()
      setupJobsListMock([])

      render(<ScheduledJobsPage />, { wrapper: createTestWrapper() })

      await openCreateForm(user)
      await user.selectOptions(screen.getByTestId('scheduled-job-type-input'), 'REPORT_EXPORT')
      await user.type(
        screen.getByTestId('scheduled-job-recipients-input'),
        'good@x.com, not-an-email, also-bad'
      )
      await user.click(screen.getByTestId('scheduled-job-form-submit'))

      const error = await screen.findByTestId('scheduled-job-recipients-error')
      expect(error).toHaveTextContent('Invalid email addresses: not-an-email, also-bad')
      expect(mockAxios.post).not.toHaveBeenCalled()
    })

    it('pre-fills recipients from the existing config when editing', async () => {
      const user = userEvent.setup()
      setupJobsListMock([
        makeJob({
          jobType: 'REPORT_EXPORT',
          config: { recipients: ['a@x.com', 'b@y.com'] },
        }),
      ])

      render(<ScheduledJobsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })
      await user.click(screen.getByTestId('edit-button-0'))

      expect(screen.getByTestId('scheduled-job-recipients-input')).toHaveValue('a@x.com, b@y.com')
    })
  })
})
