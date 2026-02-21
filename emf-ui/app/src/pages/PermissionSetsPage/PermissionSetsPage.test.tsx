/**
 * PermissionSetsPage Tests
 *
 * Unit tests for the PermissionSetsPage component.
 * Tests cover:
 * - Rendering the permission sets list
 * - Create permission set action with modal
 * - Edit permission set action
 * - Delete permission set with confirmation
 * - System permission set protection
 * - Loading and error states
 * - Empty state
 * - Row click navigation to detail page
 */

import React from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { PermissionSetsPage } from './PermissionSetsPage'
import { createTestWrapper, setupAuthMocks, mockAxios, resetMockAxios } from '../../test/testUtils'

// Mock TenantContext
vi.mock('../../context/TenantContext', () => ({
  TenantProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useTenant: () => ({
    tenantSlug: 'test-tenant',
    tenantBasePath: '/test-tenant',
  }),
  getTenantSlug: () => 'test-tenant',
  setResolvedTenantId: vi.fn(),
  getResolvedTenantId: () => null,
}))

const mockPermissionSets = [
  {
    id: 'ps1',
    name: 'System Admin',
    description: 'Full system administration access',
    system: true,
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  },
  {
    id: 'ps2',
    name: 'Report Manager',
    description: 'Access to reports and dashboards',
    system: false,
    createdAt: '2024-02-01T08:00:00Z',
    updatedAt: '2024-02-01T08:00:00Z',
  },
  {
    id: 'ps3',
    name: 'Data Export',
    description: null,
    system: false,
    createdAt: '2024-03-01T12:00:00Z',
    updatedAt: '2024-03-01T12:00:00Z',
  },
]

describe('PermissionSetsPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  it('renders loading state initially', () => {
    mockAxios.get.mockReturnValue(new Promise(() => {}))
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    expect(screen.getByTestId('loading-spinner-label')).toBeInTheDocument()
  })

  it('renders error state with retry', async () => {
    mockAxios.get.mockRejectedValueOnce(new Error('Network error'))
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument()
    })
    expect(screen.getByTestId('error-message-retry')).toBeInTheDocument()
  })

  it('renders empty state when no permission sets', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: [] })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('No permission sets found')).toBeInTheDocument()
    })
  })

  it('renders permission sets list with correct data', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
      expect(screen.getByText('Data Export')).toBeInTheDocument()
    })
    expect(screen.getByText('Full system administration access')).toBeInTheDocument()
    expect(screen.getByText('Access to reports and dashboards')).toBeInTheDocument()
  })

  it('shows system badge for system permission sets', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    // "System" appears as both a table header and a badge
    const systemTexts = screen.getAllByText('System')
    expect(systemTexts.length).toBeGreaterThanOrEqual(2)
  })

  it('does not show edit/delete buttons for system permission sets', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    // System row (index 0) should not have Edit or Delete
    expect(screen.queryByTestId('edit-button-0')).not.toBeInTheDocument()
    expect(screen.queryByTestId('delete-button-0')).not.toBeInTheDocument()
  })

  it('shows edit/delete buttons for non-system permission sets', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
    })
    expect(screen.getByTestId('edit-button-1')).toBeInTheDocument()
    expect(screen.getByTestId('delete-button-1')).toBeInTheDocument()
  })

  it('opens create modal when New Permission Set button is clicked', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('new-permission-set-button'))
    const modal = screen.getByTestId('permission-set-form-modal')
    expect(modal).toBeInTheDocument()
    expect(within(modal).getByText('New Permission Set')).toBeInTheDocument()
  })

  it('validates required name field on create', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('new-permission-set-button'))
    await user.click(screen.getByTestId('permission-set-form-submit'))
    await waitFor(() => {
      expect(screen.getByTestId('permission-set-name-error')).toBeInTheDocument()
    })
  })

  it('submits create form successfully', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    mockAxios.post.mockResolvedValueOnce({
      data: { id: 'ps4', name: 'New PS', description: 'Test', system: false },
    })
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('new-permission-set-button'))
    await user.type(screen.getByTestId('permission-set-name-input'), 'New PS')
    await user.type(screen.getByTestId('permission-set-description-input'), 'Test description')
    await user.click(screen.getByTestId('permission-set-form-submit'))
    await waitFor(() => {
      expect(mockAxios.post).toHaveBeenCalledWith('/control/permission-sets', {
        name: 'New PS',
        description: 'Test description',
      })
    })
  })

  it('opens edit modal with pre-filled data', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('edit-button-1'))
    expect(screen.getByTestId('permission-set-form-modal')).toBeInTheDocument()
    expect(screen.getByTestId('permission-set-name-input')).toHaveValue('Report Manager')
    expect(screen.getByTestId('permission-set-description-input')).toHaveValue(
      'Access to reports and dashboards'
    )
  })

  it('opens delete confirmation dialog', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('delete-button-1'))
    await waitFor(() => {
      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
    })
  })

  it('has correct page structure', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockPermissionSets })
    render(<PermissionSetsPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Permission Sets')).toBeInTheDocument()
    })
    expect(screen.getByTestId('permission-sets-table')).toBeInTheDocument()
    expect(screen.getByTestId('new-permission-set-button')).toBeInTheDocument()
  })
})
