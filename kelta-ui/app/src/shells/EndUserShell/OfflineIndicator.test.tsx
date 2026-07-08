/**
 * OfflineIndicator (Phase 4 slice 1): banners, outbox panel, retry/discard.
 * `@/offline` is mocked — online-event reactivity is covered by
 * `offline/useOnlineStatus.test.ts`, outbox behavior by `offline/useOfflineOutbox.test.tsx`.
 */
import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { I18nProvider } from '@/context/I18nContext'
import { OfflineIndicator } from './OfflineIndicator'
import type { FailedOp, OutboxOp } from '@/offline'

const state: {
  online: boolean
  pending: OutboxOp[]
  failed: FailedOp[]
  retry: ReturnType<typeof vi.fn>
  discard: ReturnType<typeof vi.fn>
} = {
  online: true,
  pending: [],
  failed: [],
  retry: vi.fn(),
  discard: vi.fn(),
}

vi.mock('@/offline', () => ({
  useOnlineStatus: () => state.online,
  useOfflineOutbox: () => ({
    pending: state.pending,
    failed: state.failed,
    pendingCount: state.pending.length,
    failedCount: state.failed.length,
    retry: state.retry,
    discard: state.discard,
  }),
}))

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <I18nProvider>{children}</I18nProvider>
)

const pendingOp: OutboxOp = {
  id: 'p1',
  collection: 'orders',
  op: 'create',
  tempId: 'tmp-1',
  recordId: 'tmp-1',
  payload: { name: 'X' },
  queuedAt: '2026-07-08T01:00:00Z',
}
const failedOp: FailedOp = {
  id: 'f1',
  collection: 'orders',
  op: 'update',
  recordId: 'o1',
  payload: { v: 2 },
  queuedAt: '2026-07-08T01:00:00Z',
  status: 422,
  error: 'credit_limit must be positive',
  failedAt: '2026-07-08T01:01:00Z',
}

describe('OfflineIndicator', () => {
  beforeEach(() => {
    state.online = true
    state.pending = []
    state.failed = []
    state.retry = vi.fn()
    state.discard = vi.fn()
  })

  it('renders nothing when online with an empty outbox', () => {
    const { container } = render(<OfflineIndicator />, { wrapper })
    expect(container).toBeEmptyDOMElement()
  })

  it('shows the plain offline banner with no queued changes', () => {
    state.online = false
    render(<OfflineIndicator />, { wrapper })
    expect(screen.getByTestId('offline-banner-text')).toHaveTextContent(/offline/i)
    expect(screen.queryByTestId('offline-details-toggle')).toBeNull()
  })

  it('shows the offline banner with the queued count', () => {
    state.online = false
    state.pending = [pendingOp]
    render(<OfflineIndicator />, { wrapper })
    expect(screen.getByTestId('offline-banner-text').textContent).toContain('1')
  })

  it('shows the failed banner while ONLINE when failed ops are retained', () => {
    state.failed = [failedOp]
    render(<OfflineIndicator />, { wrapper })
    expect(screen.getByTestId('offline-banner-text').textContent).toMatch(/failed/i)
  })

  it('expands the panel listing pending and failed ops', () => {
    state.online = false
    state.pending = [pendingOp]
    state.failed = [failedOp]
    render(<OfflineIndicator />, { wrapper })
    fireEvent.click(screen.getByTestId('offline-details-toggle'))
    expect(screen.getByTestId('outbox-pending-p1')).toBeInTheDocument()
    const failedRow = screen.getByTestId('outbox-failed-f1')
    expect(failedRow.textContent).toContain('credit_limit must be positive')
    expect(failedRow.textContent).toContain('422')
  })

  it('fires retry and discard for a failed op', () => {
    state.failed = [failedOp]
    render(<OfflineIndicator />, { wrapper })
    fireEvent.click(screen.getByTestId('offline-details-toggle'))
    fireEvent.click(screen.getByTestId('outbox-retry-f1'))
    expect(state.retry).toHaveBeenCalledWith('f1')
    fireEvent.click(screen.getByTestId('outbox-discard-f1'))
    expect(state.discard).toHaveBeenCalledWith('f1')
  })
})
