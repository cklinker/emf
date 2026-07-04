/**
 * ScriptsPage Tests
 *
 * Unit tests for the ScriptsPage component, focused on the per-script
 * execution permission (`requiredPermission`):
 * - The "Required permission to execute" select renders with the None default
 * - Saving a script sends `requiredPermission` through the create path
 * - Editing a script pre-fills the select from the existing value
 * - The scripts table surfaces the configured permission
 */

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ScriptsPage } from './ScriptsPage'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'

interface MockScript {
  id: string
  name: string
  description: string | null
  scriptType: string
  language: string
  sourceCode: string
  active: boolean
  version: number
  requiredPermission: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

function buildScript(overrides: Partial<MockScript> = {}): MockScript {
  return {
    id: 'script-1',
    name: 'Recalculate Totals',
    description: 'Recalculates order totals',
    scriptType: 'API_ENDPOINT',
    language: 'javascript',
    sourceCode: 'export default () => {}',
    active: true,
    version: 1,
    requiredPermission: null,
    createdBy: 'user-1',
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-01-15T10:00:00Z',
    ...overrides,
  }
}

describe('ScriptsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
  })

  afterEach(() => {
    vi.clearAllMocks()
    cleanupAuthMocks()
  })

  describe('Required permission select', () => {
    it('renders with the None default when creating a script', async () => {
      mockAxios.get.mockResolvedValue({ data: [] })

      const user = userEvent.setup()
      render(<ScriptsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-script-button')).toBeInTheDocument()
      })
      await user.click(screen.getByTestId('add-script-button'))

      const select = screen.getByTestId<HTMLSelectElement>('script-required-permission-input')
      expect(select).toBeInTheDocument()
      expect(select.value).toBe('')
      expect(select.options[0].textContent).toBe('None — any API user')
      // Catalog options come from the shared SYSTEM_PERMISSIONS constant
      expect(Array.from(select.options).some((option) => option.value === 'MANAGE_DATA')).toBe(true)
    })

    it('sends requiredPermission when saving a new script', async () => {
      mockAxios.get.mockResolvedValue({ data: [] })
      mockAxios.post.mockResolvedValue({
        data: {
          data: {
            type: 'scripts',
            id: 'script-new',
            attributes: buildScript({ requiredPermission: 'MANAGE_DATA' }),
          },
        },
      })

      const user = userEvent.setup()
      render(<ScriptsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('add-script-button')).toBeInTheDocument()
      })
      await user.click(screen.getByTestId('add-script-button'))

      await user.type(screen.getByTestId('script-name-input'), 'Guarded Script')
      await user.type(screen.getByTestId('script-source-code-input'), 'return 1')
      await user.selectOptions(
        screen.getByTestId('script-required-permission-input'),
        'MANAGE_DATA'
      )
      await user.click(screen.getByTestId('script-form-submit'))

      await waitFor(() => {
        expect(mockAxios.post).toHaveBeenCalledTimes(1)
      })
      const [url, body] = mockAxios.post.mock.calls[0] as [
        string,
        { data: { attributes: Record<string, unknown> } },
      ]
      expect(url).toBe('/api/scripts')
      expect(body.data.attributes.requiredPermission).toBe('MANAGE_DATA')
      expect(body.data.attributes.name).toBe('Guarded Script')
    })

    it('pre-fills the select when editing a script with a required permission', async () => {
      mockAxios.get.mockResolvedValue({
        data: [buildScript({ requiredPermission: 'VIEW_SETUP' })],
      })

      const user = userEvent.setup()
      render(<ScriptsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('edit-button-0')).toBeInTheDocument()
      })
      await user.click(screen.getByTestId('edit-button-0'))

      const select = screen.getByTestId<HTMLSelectElement>('script-required-permission-input')
      expect(select.value).toBe('VIEW_SETUP')
    })
  })

  describe('Scripts table', () => {
    it('shows the required permission label, or a dash when unset', async () => {
      mockAxios.get.mockResolvedValue({
        data: [
          buildScript({ id: 'script-1', requiredPermission: 'MANAGE_DATA' }),
          buildScript({ id: 'script-2', name: 'Open Script', requiredPermission: null }),
        ],
      })

      render(<ScriptsPage />, { wrapper: createTestWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('scripts-table')).toBeInTheDocument()
      })
      expect(screen.getByTestId('required-permission-cell-0')).toHaveTextContent('Manage Data')
      expect(screen.getByTestId('required-permission-cell-1')).toHaveTextContent('—')
    })
  })
})
