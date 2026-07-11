import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'
import { TelehealthSettingsPage } from './TelehealthSettingsPage'

const SETTINGS = { archiveAfterDays: 30, retentionYears: 7, purgeLiveAfterDays: 90 }
const ARCHIVE = {
  id: 'arch-1',
  sourceType: 'CONVERSATION',
  sourceId: 'conv-1',
  archivedAt: '2026-01-01T00:00:00Z',
  retentionUntil: '2033-01-01T00:00:00Z',
  legalHold: false,
  purgedAt: null,
}

function stubGets(canManage: boolean) {
  mockAxios.get.mockImplementation((url: string) => {
    if (url === '/api/me/permissions') {
      return Promise.resolve({
        data: {
          systemPermissions: { MANAGE_DATA: canManage, VIEW_SETUP: true },
          objectPermissions: {},
          fieldPermissions: {},
        },
      })
    }
    if (url === '/api/telehealth/retention-settings') {
      return Promise.resolve({ data: SETTINGS })
    }
    if (url.startsWith('/api/telehealth/archives')) {
      return Promise.resolve({ data: { data: [ARCHIVE] } })
    }
    return Promise.reject(new Error(`Unexpected GET: ${url}`))
  })
}

describe('TelehealthSettingsPage', () => {
  beforeEach(() => {
    resetMockAxios()
    setupAuthMocks()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the retention settings and archive list', async () => {
    stubGets(true)
    render(<TelehealthSettingsPage />, { wrapper: createTestWrapper() })

    const archiveAfter = (await screen.findByTestId(
      'telehealth-settings-archiveAfterDays'
    )) as HTMLInputElement
    expect(archiveAfter.value).toBe('30')
    expect(
      (screen.getByTestId('telehealth-settings-retentionYears') as HTMLInputElement).value
    ).toBe('7')
    // The archive list row renders.
    expect(await screen.findByTestId('telehealth-settings-row-arch-1')).toBeInTheDocument()
  })

  it('saves edited retention settings via PUT', async () => {
    stubGets(true)
    mockAxios.put.mockResolvedValue({ data: SETTINGS })
    render(<TelehealthSettingsPage />, { wrapper: createTestWrapper() })

    const retention = (await screen.findByTestId(
      'telehealth-settings-retentionYears'
    )) as HTMLInputElement
    fireEvent.change(retention, { target: { value: '10' } })
    await waitFor(() => expect(retention.value).toBe('10'))

    await userEvent.setup().click(screen.getByTestId('telehealth-settings-save'))
    await waitFor(() => {
      expect(mockAxios.put).toHaveBeenCalledWith(
        '/api/telehealth/retention-settings',
        expect.objectContaining({ retentionYears: 10 }),
        undefined
      )
    })
  })

  it('confirms before toggling legal hold and calls the endpoint on confirm', async () => {
    stubGets(true)
    mockAxios.post.mockResolvedValue({ data: { id: 'arch-1', legalHold: true } })
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<TelehealthSettingsPage />, { wrapper: createTestWrapper() })

    const holdBtn = await screen.findByTestId('telehealth-settings-hold-arch-1')
    await userEvent.setup().click(holdBtn)

    expect(confirmSpy).toHaveBeenCalled()
    await waitFor(() => {
      expect(mockAxios.post).toHaveBeenCalledWith('/api/telehealth/archives/arch-1/legal-hold', {
        enabled: true,
      })
    })
    confirmSpy.mockRestore()
  })

  it('does not call the endpoint when the confirm dialog is cancelled', async () => {
    stubGets(true)
    mockAxios.post.mockResolvedValue({ data: {} })
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    render(<TelehealthSettingsPage />, { wrapper: createTestWrapper() })

    const holdBtn = await screen.findByTestId('telehealth-settings-hold-arch-1')
    await userEvent.setup().click(holdBtn)

    expect(confirmSpy).toHaveBeenCalled()
    expect(mockAxios.post).not.toHaveBeenCalled()
    confirmSpy.mockRestore()
  })

  it('hides the save button and legal-hold toggle without MANAGE_DATA', async () => {
    stubGets(false)
    render(<TelehealthSettingsPage />, { wrapper: createTestWrapper() })

    await screen.findByTestId('telehealth-settings-row-arch-1')
    expect(screen.queryByTestId('telehealth-settings-save')).not.toBeInTheDocument()
    expect(screen.queryByTestId('telehealth-settings-hold-arch-1')).not.toBeInTheDocument()
  })
})
