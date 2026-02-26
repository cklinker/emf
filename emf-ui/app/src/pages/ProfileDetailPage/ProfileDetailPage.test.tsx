/**
 * ProfileDetailPage Tests
 *
 * Unit tests for the ProfileDetailPage component.
 * Tests cover:
 * - Rendering profile detail with basic info
 * - System permissions display and editing
 * - Object permissions display
 * - Delete functionality
 * - System profile protection
 * - Loading and error states
 */

import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ProfileDetailPage } from './ProfileDetailPage'
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
    useParams: () => ({ id: 'prof1' }),
    useNavigate: () => vi.fn(),
  }
})

// JSON:API formatted profile response with included permissions
function makeProfileResponse(
  profile: { id: string; name: string; description: string; isSystem: boolean },
  sysPerms: Array<{
    id: string
    profileId: string
    permissionName: string
    granted: boolean
  }> = mockSysPerms,
  objPerms: Array<{
    id: string
    profileId: string
    collectionId: string
    canCreate: boolean
    canRead: boolean
    canEdit: boolean
    canDelete: boolean
    canViewAll: boolean
    canModifyAll: boolean
  }> = mockObjPerms
) {
  return {
    data: {
      type: 'profiles',
      id: profile.id,
      attributes: {
        name: profile.name,
        description: profile.description,
        isSystem: profile.isSystem,
        createdAt: '2024-01-15T10:00:00Z',
        updatedAt: '2024-01-15T10:00:00Z',
      },
    },
    included: [
      ...sysPerms.map((sp) => ({
        type: 'profile-system-permissions',
        id: sp.id,
        attributes: {
          permissionName: sp.permissionName,
          granted: sp.granted,
        },
        relationships: {
          profileId: { data: { type: 'profiles', id: sp.profileId } },
        },
      })),
      ...objPerms.map((op) => ({
        type: 'profile-object-permissions',
        id: op.id,
        attributes: {
          canCreate: op.canCreate,
          canRead: op.canRead,
          canEdit: op.canEdit,
          canDelete: op.canDelete,
          canViewAll: op.canViewAll,
          canModifyAll: op.canModifyAll,
        },
        relationships: {
          profileId: { data: { type: 'profiles', id: op.profileId } },
          collectionId: { data: { type: 'collections', id: op.collectionId } },
        },
      })),
    ],
  }
}

const mockProfileAttrs = {
  id: 'prof1',
  name: 'Standard User',
  description: 'Default profile for standard users',
  isSystem: false,
}

const mockSystemProfileAttrs = {
  id: 'prof2',
  name: 'System Admin',
  description: 'Default profile for standard users',
  isSystem: true,
}

const mockSysPerms = [
  { id: 'sp1', profileId: 'prof1', permissionName: 'VIEW_SETUP', granted: true },
  { id: 'sp2', profileId: 'prof1', permissionName: 'MANAGE_USERS', granted: true },
  { id: 'sp3', profileId: 'prof1', permissionName: 'API_ACCESS', granted: false },
]

const mockObjPerms = [
  {
    id: 'op1',
    profileId: 'prof1',
    collectionId: 'col1',
    canCreate: true,
    canRead: true,
    canEdit: true,
    canDelete: false,
    canViewAll: false,
    canModifyAll: false,
  },
]

const mockCollections = {
  content: [{ id: 'col1', name: 'accounts', displayName: 'Accounts' }],
  totalElements: 1,
  totalPages: 1,
  size: 1000,
  number: 0,
}

function setupMocks(profileAttrs = mockProfileAttrs) {
  mockAxios.get.mockImplementation((url: string) => {
    if (url.includes('/api/collections')) return Promise.resolve({ data: mockCollections })
    if (url.includes('/api/profiles/'))
      return Promise.resolve({ data: makeProfileResponse(profileAttrs) })
    return Promise.resolve({ data: {} })
  })
}

describe('ProfileDetailPage', () => {
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
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    expect(screen.getByTestId('loading-spinner-label')).toBeInTheDocument()
  })

  it('renders error state with retry', async () => {
    mockAxios.get.mockRejectedValue(new Error('Network error'))
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument()
    })
    expect(screen.getByTestId('error-message-retry')).toBeInTheDocument()
  })

  it('renders profile detail with basic info', async () => {
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    expect(screen.getByText('Default profile for standard users')).toBeInTheDocument()
  })

  it('renders system badge for system profiles', async () => {
    setupMocks(mockSystemProfileAttrs)
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    expect(screen.getByTestId('system-badge')).toBeInTheDocument()
  })

  it('renders system permissions section', async () => {
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Permissions')).toBeInTheDocument()
    })
    expect(screen.getByTestId('system-permissions-section')).toBeInTheDocument()
  })

  it('shows edit permissions button for non-system profiles', async () => {
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    expect(screen.getByTestId('edit-permissions-button')).toBeInTheDocument()
  })

  it('hides edit permissions button for system profiles', async () => {
    setupMocks(mockSystemProfileAttrs)
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    expect(screen.queryByTestId('edit-permissions-button')).not.toBeInTheDocument()
  })

  it('shows save/cancel buttons when editing permissions', async () => {
    const user = userEvent.setup()
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('edit-permissions-button'))
    expect(screen.getByTestId('save-permissions-button')).toBeInTheDocument()
    expect(screen.getByTestId('cancel-edit-button')).toBeInTheDocument()
    expect(screen.queryByTestId('edit-permissions-button')).not.toBeInTheDocument()
  })

  it('cancels editing and hides save/cancel buttons', async () => {
    const user = userEvent.setup()
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('edit-permissions-button'))
    expect(screen.getByTestId('save-permissions-button')).toBeInTheDocument()
    await user.click(screen.getByTestId('cancel-edit-button'))
    expect(screen.queryByTestId('save-permissions-button')).not.toBeInTheDocument()
    expect(screen.getByTestId('edit-permissions-button')).toBeInTheDocument()
  })

  it('shows delete button for non-system profiles', async () => {
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    expect(screen.getByTestId('delete-button')).toBeInTheDocument()
  })

  it('hides delete button for system profiles', async () => {
    setupMocks(mockSystemProfileAttrs)
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('System Admin')).toBeInTheDocument()
    })
    expect(screen.queryByTestId('delete-button')).not.toBeInTheDocument()
  })

  it('opens delete confirmation dialog', async () => {
    const user = userEvent.setup()
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('delete-button'))
    await waitFor(() => {
      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
    })
  })

  it('has back link to profiles list', async () => {
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    expect(screen.getByTestId('back-link')).toBeInTheDocument()
    expect(screen.getByText('Back to Profiles')).toBeInTheDocument()
  })

  it('renders object permissions section with collection names', async () => {
    setupMocks()
    render(<ProfileDetailPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await waitFor(() => {
      expect(screen.getByTestId('object-permissions-section')).toBeInTheDocument()
    })
  })
})
