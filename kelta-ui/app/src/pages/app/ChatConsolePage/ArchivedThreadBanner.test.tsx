import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createTestWrapper,
  setupAuthMocks,
  mockAxios,
  resetMockAxios,
} from '../../../test/testUtils'
import { ArchivedThreadBanner } from './ArchivedThreadBanner'
import type { ArchiveSummary } from '../../../hooks/useArchives'

const ARCHIVE: ArchiveSummary = {
  id: 'arch-1',
  sourceType: 'CONVERSATION',
  sourceId: 'conv-1',
  archivedAt: '2026-01-15T00:00:00Z',
  retentionUntil: '2033-01-15T00:00:00Z',
  legalHold: false,
  purgedAt: null,
}

describe('ArchivedThreadBanner', () => {
  beforeEach(() => {
    resetMockAxios()
    setupAuthMocks()
    mockAxios.get.mockImplementation((url: string) => {
      if (url === '/api/me/permissions') {
        return Promise.resolve({
          data: { systemPermissions: {}, objectPermissions: {}, fieldPermissions: {} },
        })
      }
      if (url === '/api/telehealth/archives/arch-1') {
        return Promise.resolve({
          data: {
            ...ARCHIVE,
            artifacts: [
              {
                id: 'att-json',
                fileName: 'conversation-conv-1.json',
                contentType: 'application/json',
                downloadUrl: 'https://s3.example/json?sig=abc',
              },
              {
                id: 'att-pdf',
                fileName: 'conversation-conv-1.pdf',
                contentType: 'application/pdf',
                downloadUrl: 'https://s3.example/pdf?sig=def',
              },
            ],
          },
        })
      }
      return Promise.reject(new Error(`Unexpected GET: ${url}`))
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the archived + retained-until banner read-only', async () => {
    render(<ArchivedThreadBanner archive={ARCHIVE} />, { wrapper: createTestWrapper() })
    // The banner text interpolates both dates.
    expect(await screen.findByText(/Archived/)).toBeInTheDocument()
    expect(screen.getByText(/retained until/)).toBeInTheDocument()
    // No composer / send affordance in a read-only banner.
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('opens the presigned PDF URL on download', async () => {
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null)
    render(<ArchivedThreadBanner archive={ARCHIVE} />, { wrapper: createTestWrapper() })

    const pdfBtn = await screen.findByTestId('chat-console-archive-banner-pdf')
    const user = userEvent.setup()
    // First click triggers the detail fetch; once resolved a click opens the URL.
    await user.click(pdfBtn)
    await waitFor(() =>
      expect(mockAxios.get).toHaveBeenCalledWith('/api/telehealth/archives/arch-1')
    )
    await user.click(pdfBtn)

    await waitFor(() =>
      expect(openSpy).toHaveBeenCalledWith(
        'https://s3.example/pdf?sig=def',
        '_blank',
        'noopener,noreferrer'
      )
    )
    openSpy.mockRestore()
  })
})
