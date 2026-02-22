/**
 * PermissionSetDetailPage Tests
 *
 * Unit tests for the PermissionSetDetailPage component.
 * Tests cover:
 * - Rendering permission set detail with basic info
 * - System permissions display (editable for non-system)
 * - Object permissions display
 * - Assignments display (users and groups)
 * - Delete functionality
 * - System permission set protection
 * - Loading and error states
 */

import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { PermissionSetDetailPage } from './PermissionSetDetailPage'
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

// Mock useParams and useNavigate
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useParams: () => ({ id: 'ps1' }),
    useNavigate: () => vi.fn(),
  }
})

const mockPermissionSet = {
  id: 'ps1',
  name: 'Report Manager',
  description: 'Access to reports and dashboards',
  system: false,
  createdAt: '2024-02-01T08:00:00Z',
  updatedAt: '2024-02-10T14:00:00Z',
}

const mockSystemPermissionSet = {
  ...mockPermissionSet,
  id: 'ps2',
  name: 'System Admin',
  system: true,
}

const mockSystemPermissions = [
  { id: 'sp1', permissionSetId: 'ps1', permissionName: 'MANAGE_USERS', granted: true },
  { id: 'sp2', permissionSetId: 'ps1', permissionName: 'VIEW_SETUP', granted: false },
]

const mockAssignments = {
  users: [
    { id: 'ua1', userId: 'user-1', permissionSetId: 'ps1', createdAt: '2024-03-01T10:00:00Z' },
    { id: 'ua2', userId: 'user-2', permissionSetId: 'ps1', createdAt: '2024-03-02T10:00:00Z' },
  ],
  groups: [
    {
      id: 'ga1',
      groupId: 'group-1',
      permissionSetId: 'ps1',
      createdAt: '2024-03-01T10:00:00Z',
    },
  ],
}

const emptyAssignments = {
  users: [],
  groups: [],
}

function setupMocks(permSet = mockPermissionSet) {
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes('/system-permissions')) {
      return Promise.resolve({ data: mockSystemPermissions })
    }
    if (url.includes('/object-permissions')) {
      return Promise.resolve({ data: [] })
    }
    if (url.includes('/assignments')) {
      return Promise.resolve({ data: mockAssignments })
    }
    if (url.includes('/collections')) {
      return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } })
    }
    return Promise.resolve({ data: permSet })
  })
}

function setupMocksWithEmptyAssignments(permSet = mockPermissionSet) {
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes('/system-permissions')) {
      return Promise.resolve({ data: [] })
    }
    if (url.includes('/object-permissions')) {
      return Promise.resolve({ data: [] })
    }
    if (url.includes('/assignments')) {
      return Promise.resolve({ data: emptyAssignments })
    }
    if (url.includes('/collections')) {
      return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } })
    }
    return Promise.resolve({ data: permSet })
  })
}

describe('PermissionSetDetailPage', () => {
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
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    expect(screen.getByTestId('loading-spinner-label')).toBeInTheDocument()
  })

  it('renders error state with retry', async () => {
    mockAxios.get.mockRejectedValue(new Error('Network error'))
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument()
    })
    expect(screen.getByTestId('error-message-retry')).toBeInTheDocument()
  })

  it('renders permission set detail with basic info', async () => {
    setupMocks()
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
    })
    expect(screen.getByText('Access to reports and dashboards')).toBeInTheDocument()
  })

  it('renders system permissions section', async () => {
    setupMocks()
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Permissions')).toBeInTheDocument()
    })
    expect(screen.getByTestId('system-permissions-section')).toBeInTheDocument()
  })

  it('shows edit permissions button for non-system permission sets', async () => {
    setupMocks()
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
    })
    expect(screen.getByTestId('edit-permissions-button')).toBeInTheDocument()
  })

  it('does not show edit permissions button for system permission sets', async () => {
    setupMocks(mockSystemPermissionSet)
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    expect(screen.queryByTestId('edit-permissions-button')).not.toBeInTheDocument()
  })

  it('renders user and group assignments', async () => {
    setupMocks()
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Assigned Users (2)')).toBeInTheDocument()
    })
    expect(screen.getByText('Assigned Groups (1)')).toBeInTheDocument()
    expect(screen.getByText('user-1')).toBeInTheDocument()
    expect(screen.getByText('user-2')).toBeInTheDocument()
    expect(screen.getByText('group-1')).toBeInTheDocument()
  })

  it('renders empty assignment messages', async () => {
    setupMocksWithEmptyAssignments()
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByTestId('no-user-assignments')).toBeInTheDocument()
    })
    expect(screen.getByTestId('no-group-assignments')).toBeInTheDocument()
  })

  it('shows delete button for non-system permission sets', async () => {
    setupMocksWithEmptyAssignments()
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
    })
    expect(screen.getByTestId('delete-button')).toBeInTheDocument()
  })

  it('does not show delete button for system permission sets', async () => {
    setupMocksWithEmptyAssignments(mockSystemPermissionSet)
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    expect(screen.queryByTestId('delete-button')).not.toBeInTheDocument()
  })

  it('opens delete confirmation dialog', async () => {
    const user = userEvent.setup()
    setupMocksWithEmptyAssignments()
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('delete-button'))
    await waitFor(() => {
      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
    })
  })

  it('has back link to permission sets list', async () => {
    setupMocksWithEmptyAssignments()
    render(<PermissionSetDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Report Manager')).toBeInTheDocument()
    })
    expect(screen.getByTestId('back-link')).toBeInTheDocument()
    expect(screen.getByText('Back to Permission Sets')).toBeInTheDocument()
  })
})
