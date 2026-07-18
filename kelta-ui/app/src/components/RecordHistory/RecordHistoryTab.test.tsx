/**
 * RecordHistoryTab tests
 *
 * Renders the History tab against a mocked /api/record-versions feed:
 * empty state, newest-first version list with author + change summary,
 * drill-down into a version with changed-field badges (flat-grid fallback,
 * no layout sections), back navigation, and initialVersion preselection.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { RecordHistoryTab } from './RecordHistoryTab'
import type { RecordHistoryTabProps } from './RecordHistoryTab'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'

// Mock API context — the hook under the tab fetches /api/record-versions.
const { mockGetList } = vi.hoisted(() => ({ mockGetList: vi.fn() }))
vi.mock('@/context/ApiContext', () => ({
  useApi: vi.fn(() => ({ apiClient: { getList: mockGetList } })),
}))

// Mock I18n with the history keys + {{param}} interpolation so assertions can
// target the interpolated user/field values.
vi.mock('@/context/I18nContext', () => {
  const translations: Record<string, string> = {
    'common.loading': 'Loading...',
    'history.empty': 'No versions recorded yet.',
    'history.loadError': 'Failed to load record history.',
    'history.versionLabel': 'Version {{version}}',
    'history.changedBadge': 'Changed',
    'history.recordCreated': 'Record created',
    'history.recordUpdated': 'Record updated',
    'history.recordDeleted': 'Record deleted',
    'history.fieldsChangedSummary': '{{count}} fields changed: {{fields}}',
    'history.backToList': 'All versions',
    'history.byUser': 'by {{user}}',
  }
  return {
    useI18n: vi.fn(() => ({
      locale: 'en',
      setLocale: vi.fn(),
      t: (key: string, params?: Record<string, string | number> | string) => {
        let text = translations[key] ?? key
        if (params && typeof params === 'object') {
          for (const [name, value] of Object.entries(params)) {
            text = text.replace(`{{${name}}}`, String(value))
          }
        }
        return text
      },
      formatDate: (date: Date) => date.toISOString(),
    })),
  }
})

const schemaFields: FieldDefinition[] = [
  { id: 'f1', name: 'name', displayName: 'Full Name', type: 'string', required: false },
  { id: 'f2', name: 'status', displayName: 'Status', type: 'string', required: false },
]

// Raw rows as served by /api/record-versions (oldest first — the hook re-sorts).
const rawVersionRows = [
  {
    id: 'ver-1',
    collectionId: 'col-1',
    recordId: 'rec-1',
    versionNumber: 1,
    changeType: 'CREATED',
    snapshot: { id: 'rec-1', name: 'Acme', status: 'new' },
    changedFields: [],
    changedBy: 'user-1',
    changedAt: '2026-07-01T10:00:00Z',
    changeSource: 'UI',
  },
  {
    id: 'ver-2',
    collectionId: 'col-1',
    recordId: 'rec-1',
    versionNumber: 2,
    changeType: 'UPDATED',
    snapshot: { id: 'rec-1', name: 'Acme Corp', status: 'new' },
    changedFields: ['name'],
    changedBy: 'user-1',
    changedAt: '2026-07-02T10:00:00Z',
    changeSource: 'UI',
  },
]

function renderTab(props: Partial<RecordHistoryTabProps> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RecordHistoryTab
          collectionId="col-1"
          recordId="rec-1"
          sections={undefined}
          schemaFields={schemaFields}
          tenantSlug="acme"
          getUserDisplay={(userId) => (userId === 'user-1' ? { name: 'Jane Doe' } : null)}
          {...props}
        />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('RecordHistoryTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the empty state when there are no versions', async () => {
    mockGetList.mockResolvedValue([])

    renderTab()

    await waitFor(() => expect(screen.getByTestId('history-empty')).toBeInTheDocument())
    expect(screen.getByText('No versions recorded yet.')).toBeInTheDocument()
    expect(mockGetList).toHaveBeenCalledWith(
      expect.stringContaining(
        '/api/record-versions?filter[collectionId][eq]=col-1&filter[recordId][eq]=rec-1'
      )
    )
  })

  it('renders the error state when the fetch fails', async () => {
    mockGetList.mockRejectedValue(new Error('boom'))

    renderTab()

    await waitFor(() => expect(screen.getByTestId('history-error')).toBeInTheDocument())
  })

  it('renders version rows newest-first with author and change summary', async () => {
    mockGetList.mockResolvedValue(rawVersionRows)

    renderTab()

    await waitFor(() => expect(screen.getByTestId('record-version-list')).toBeInTheDocument())
    const rows = screen.getAllByTestId('record-version-row')
    expect(rows).toHaveLength(2)

    // Newest first: v2 (updated) before v1 (created).
    expect(rows[0]).toHaveAttribute('data-version', '2')
    expect(rows[1]).toHaveAttribute('data-version', '1')

    // Summary uses the schema display name; author resolves via getUserDisplay.
    expect(within(rows[0]).getByText('1 fields changed: Full Name')).toBeInTheDocument()
    expect(within(rows[0]).getByText(/by Jane Doe/)).toBeInTheDocument()
    expect(within(rows[1]).getByText('Record created')).toBeInTheDocument()
  })

  it('shows the version detail with badges on changed fields only when a row is clicked', async () => {
    const user = userEvent.setup()
    mockGetList.mockResolvedValue(rawVersionRows)

    renderTab()

    await waitFor(() => expect(screen.getByTestId('record-version-list')).toBeInTheDocument())
    await user.click(screen.getAllByTestId('record-version-row')[0]) // v2

    expect(screen.getByTestId('record-version-detail')).toBeInTheDocument()
    // No layout sections were passed, so the flat grid fallback renders.
    expect(screen.getByTestId('version-detail-fallback')).toBeInTheDocument()

    // Only the edited field ("name") is badged — identified by its snapshot value.
    const changedFields = screen.getAllByTestId('version-changed-field')
    expect(changedFields).toHaveLength(1)
    expect(within(changedFields[0]).getByTestId('version-changed-badge')).toBeInTheDocument()
    expect(within(changedFields[0]).getByText('Acme Corp')).toBeInTheDocument()
    expect(screen.getAllByTestId('version-changed-badge')).toHaveLength(1)
  })

  it('returns to the version list via the back button', async () => {
    const user = userEvent.setup()
    mockGetList.mockResolvedValue(rawVersionRows)

    renderTab()

    await waitFor(() => expect(screen.getByTestId('record-version-list')).toBeInTheDocument())
    await user.click(screen.getAllByTestId('record-version-row')[0])
    expect(screen.getByTestId('record-version-detail')).toBeInTheDocument()

    await user.click(screen.getByTestId('history-back-button'))

    expect(screen.getByTestId('record-version-list')).toBeInTheDocument()
    expect(screen.queryByTestId('record-version-detail')).not.toBeInTheDocument()
  })

  it('preselects the version given by initialVersion', async () => {
    mockGetList.mockResolvedValue(rawVersionRows)

    renderTab({ initialVersion: 2 })

    await waitFor(() => expect(screen.getByTestId('record-version-detail')).toBeInTheDocument())
    expect(screen.queryByTestId('record-version-list')).not.toBeInTheDocument()
    // The selected-version header identifies v2.
    expect(screen.getByText(/Version 2/)).toBeInTheDocument()
  })
})
