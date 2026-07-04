import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { ActivityTimeline } from './ActivityTimeline'
import type { ApiClient } from '../../services/apiClient'

// Mock I18n — echo the key so assertions can target stable strings.
vi.mock('../../context/I18nContext', () => ({
  useI18n: vi.fn(() => ({
    locale: 'en',
    setLocale: vi.fn(),
    t: (key: string) => key,
  })),
}))

function renderTimeline(apiClient: Partial<ApiClient>) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    React.createElement(
      QueryClientProvider,
      { client: qc },
      React.createElement(ActivityTimeline, {
        collectionId: 'col-1',
        collectionName: 'orders',
        recordId: 'rec-1',
        recordCreatedAt: '2026-01-01T00:00:00Z',
        apiClient: apiClient as ApiClient,
      })
    )
  )
}

describe('ActivityTimeline', () => {
  it('merges a note from /api/notes into the feed', async () => {
    const getList = vi.fn((url: string) => {
      if (url.startsWith('/api/notes')) {
        return Promise.resolve([
          { id: 'n1', content: 'Called the customer', createdAt: '2026-02-01T00:00:00Z' },
        ])
      }
      return Promise.resolve([]) // approvals + shares empty
    })

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText('Called the customer')).toBeInTheDocument())
    // The notes endpoint was queried for this record.
    expect(getList).toHaveBeenCalledWith(
      expect.stringContaining(
        '/api/notes?filter[collectionId][eq]=col-1&filter[recordId][eq]=rec-1'
      )
    )
  })

  it('truncates long note content', async () => {
    const long = 'x'.repeat(200)
    const getList = vi.fn((url: string) =>
      url.startsWith('/api/notes')
        ? Promise.resolve([{ id: 'n1', content: long, createdAt: '2026-02-01T00:00:00Z' }])
        : Promise.resolve([])
    )

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText(/x{140}…/)).toBeInTheDocument())
  })

  it('merges an attachment from /api/attachments into the feed', async () => {
    const getList = vi.fn((url: string) => {
      if (url.startsWith('/api/attachments')) {
        return Promise.resolve([
          { id: 'a1', fileName: 'contract.pdf', uploadedAt: '2026-02-02T00:00:00Z' },
        ])
      }
      return Promise.resolve([])
    })

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText(/contract\.pdf/)).toBeInTheDocument())
    expect(getList).toHaveBeenCalledWith(
      expect.stringContaining(
        '/api/attachments?filter[collectionId][eq]=col-1&filter[recordId][eq]=rec-1'
      )
    )
  })

  it('merges a flow run from /api/flows/record-executions into the feed', async () => {
    const getList = vi.fn((url: string) => {
      if (url.startsWith('/api/flows/record-executions')) {
        return Promise.resolve([
          {
            id: 'fx1',
            flowId: 'flow-1',
            flowName: 'Send Welcome',
            status: 'COMPLETED',
            startedAt: '2026-02-03T00:00:00Z',
            completedAt: '2026-02-03T00:00:05Z',
            durationMs: 5000,
            errorMessage: null,
          },
        ])
      }
      return Promise.resolve([])
    })

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText('activity.flowRun')).toBeInTheDocument())
    expect(getList).toHaveBeenCalledWith(
      expect.stringContaining('/api/flows/record-executions?recordId=rec-1&limit=20')
    )
  })

  it('merges an email log from /api/email/logs into the feed', async () => {
    const getList = vi.fn((url: string) => {
      if (url.startsWith('/api/email/logs')) {
        return Promise.resolve([
          {
            id: 'em1',
            recipientEmail: 'jane@example.com',
            subject: 'Order shipped',
            status: 'SENT',
            sentAt: '2026-02-04T00:00:00Z',
            createdAt: '2026-02-03T23:59:00Z',
          },
        ])
      }
      return Promise.resolve([])
    })

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText('activity.emailSent')).toBeInTheDocument())
    expect(getList).toHaveBeenCalledWith(
      expect.stringContaining('/api/email/logs?recordId=rec-1&limit=20')
    )
  })

  it('sorts flow runs and emails into the merged feed newest-first', async () => {
    const getList = vi.fn((url: string) => {
      if (url.startsWith('/api/flows/record-executions')) {
        return Promise.resolve([
          {
            id: 'fx1',
            flowId: 'flow-1',
            flowName: 'Send Welcome',
            status: 'FAILED',
            startedAt: '2026-03-01T00:00:00Z',
            completedAt: null,
            durationMs: null,
            errorMessage: 'boom',
          },
        ])
      }
      if (url.startsWith('/api/email/logs')) {
        return Promise.resolve([
          {
            id: 'em1',
            recipientEmail: 'jane@example.com',
            subject: 'Order shipped',
            status: 'QUEUED',
            sentAt: null,
            createdAt: '2026-02-01T00:00:00Z',
          },
        ])
      }
      if (url.startsWith('/api/notes')) {
        return Promise.resolve([
          { id: 'n1', content: 'Middle note', createdAt: '2026-02-15T00:00:00Z' },
        ])
      }
      return Promise.resolve([])
    })

    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText('activity.flowRun')).toBeInTheDocument())
    await waitFor(() => expect(screen.getByText('activity.emailSent')).toBeInTheDocument())

    const items = screen.getAllByRole('listitem')
    const ids = items.map((item) => item.getAttribute('data-testid'))
    // Newest first: flow run (Mar 1) > note (Feb 15) > email queued (Feb 1, falls
    // back to createdAt when sentAt is null) > record created (Jan 1).
    expect(ids).toEqual([
      'activity-entry-flow-run-fx1',
      'activity-entry-note-n1',
      'activity-entry-email-em1',
      'activity-entry-record-created',
    ])
  })

  it('still renders lifecycle events when there are no notes', async () => {
    const getList = vi.fn(() => Promise.resolve([]))
    renderTimeline({ getList: getList as unknown as ApiClient['getList'] })

    await waitFor(() => expect(screen.getByText('activity.recordCreated')).toBeInTheDocument())
  })
})
