import React from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createTestWrapper,
  setupAuthMocks,
  mockAxios,
  resetMockAxios,
} from '../../test/testUtils'
import { EmailSettingsPage } from './EmailSettingsPage'

describe('EmailSettingsPage', () => {
  beforeEach(() => {
    resetMockAxios()
    setupAuthMocks()
    mockAxios.get.mockImplementation((url: string) => {
      if (url === '/api/me/permissions') {
        return Promise.resolve({
          data: {
            systemPermissions: { MANAGE_TENANTS: true },
            objectPermissions: {},
            fieldPermissions: {},
          },
        })
      }
      if (url === '/api/admin/tenant/email-settings') {
        return Promise.resolve({
          data: {
            data: {
              hasOverride: false,
              fromAddress: null,
              fromName: null,
              autoInviteOnCreate: true,
            },
          },
        })
      }
      return Promise.reject(new Error(`Unexpected GET: ${url}`))
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the platform-default copy when no override is set', async () => {
    render(<EmailSettingsPage />, { wrapper: createTestWrapper() })

    await waitFor(() => {
      expect(screen.getByText(/platform default SMTP server/i)).toBeInTheDocument()
    })
    expect(screen.getByText(/Send invite emails automatically/)).toBeInTheDocument()
  })

  it('saves SMTP settings via PUT', async () => {
    mockAxios.put.mockResolvedValue({ data: { status: 'ok' } })
    const user = userEvent.setup()
    render(<EmailSettingsPage />, { wrapper: createTestWrapper() })

    await screen.findByText(/platform default SMTP server/i)
    const hostInput = screen.getByPlaceholderText('smtp.example.com') as HTMLInputElement
    fireEvent.change(hostInput, { target: { value: 'smtp.acme.com' } })
    await waitFor(() => expect(hostInput.value).toBe('smtp.acme.com'))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(mockAxios.put).toHaveBeenCalledWith(
        '/api/admin/tenant/email-settings',
        expect.objectContaining({ host: 'smtp.acme.com' })
      )
    })
  })

  it('triggers test-send POST', async () => {
    mockAxios.post.mockResolvedValue({ data: { emailLogId: 'log-1', status: 'QUEUED' } })
    const user = userEvent.setup()
    render(<EmailSettingsPage />, { wrapper: createTestWrapper() })

    await waitFor(() => screen.getByText(/Test delivery/))
    await user.type(screen.getByPlaceholderText('me@example.com'), 'qa@example.com')
    await user.click(screen.getByRole('button', { name: /Send test/i }))

    await waitFor(() => {
      expect(mockAxios.post).toHaveBeenCalledWith(
        '/api/admin/tenant/email-settings/test',
        { to: 'qa@example.com' }
      )
    })
  })
})
