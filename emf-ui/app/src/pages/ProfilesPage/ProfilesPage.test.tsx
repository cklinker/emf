/**
 * ProfilesPage Tests
 *
 * Unit tests for the ProfilesPage component.
 * Tests cover:
 * - Rendering the profiles list
 * - Create profile action with modal
 * - Edit profile action
 * - Clone profile action
 * - Delete profile with confirmation
 * - System profile protection (no edit/delete)
 * - Loading and error states
 * - Empty state
 * - Row click navigation to detail page
 */

import React from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ProfilesPage } from './ProfilesPage'
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

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

const mockProfiles = [
  {
    id: '1',
    name: 'Standard User',
    description: 'Default profile for standard users',
    isSystem: true,
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  },
  {
    id: '2',
    name: 'Admin Profile',
    description: 'Full admin access',
    isSystem: false,
    createdAt: '2024-02-01T08:00:00Z',
    updatedAt: '2024-02-01T08:00:00Z',
  },
]

describe('ProfilesPage', () => {
  let cleanupAuthMocks: () => void

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks()
    resetMockAxios()
    mockNavigate.mockReset()
  })

  afterEach(() => {
    cleanupAuthMocks()
    vi.restoreAllMocks()
  })

  it('renders loading state initially', () => {
    mockAxios.get.mockReturnValue(new Promise(() => {}))
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('renders error state with retry button', async () => {
    mockAxios.get.mockRejectedValueOnce(new Error('Network error'))
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Failed to load profiles.')).toBeInTheDocument()
    })
    expect(screen.getByText('Retry')).toBeInTheDocument()
  })

  it('renders empty state when no profiles', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: [] })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(
        screen.getByText('No profiles found. Create your first profile to get started.')
      ).toBeInTheDocument()
    })
  })

  it('renders profiles list with correct data', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
      expect(screen.getByText('Admin Profile')).toBeInTheDocument()
    })
    expect(screen.getByText('Default profile for standard users')).toBeInTheDocument()
    expect(screen.getByText('Full admin access')).toBeInTheDocument()
  })

  it('shows system badge for system profiles', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    // "System" appears as both a table header and a badge; check multiple exist
    const systemTexts = screen.getAllByText('System')
    expect(systemTexts.length).toBeGreaterThanOrEqual(2) // header + badge
  })

  it('does not show edit/delete buttons for system profiles', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    // System profile row (index 0) should have Clone but NOT Edit/Delete
    const systemRow = screen.getByTestId('profile-row-0')
    expect(within(systemRow).queryByTestId('edit-button-0')).not.toBeInTheDocument()
    expect(within(systemRow).queryByTestId('delete-button-0')).not.toBeInTheDocument()
    expect(screen.getByTestId('clone-button-0')).toBeInTheDocument()
  })

  it('shows edit/delete buttons for non-system profiles', async () => {
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Admin Profile')).toBeInTheDocument()
    })
    expect(screen.getByTestId('edit-button-1')).toBeInTheDocument()
    expect(screen.getByTestId('delete-button-1')).toBeInTheDocument()
    expect(screen.getByTestId('clone-button-1')).toBeInTheDocument()
  })

  it('opens create modal when New Profile button is clicked', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('new-profile-button'))
    const modal = screen.getByTestId('profile-form-modal')
    expect(modal).toBeInTheDocument()
    expect(within(modal).getByText('New Profile')).toBeInTheDocument()
  })

  it('validates required name field on create', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('new-profile-button'))
    await user.click(screen.getByTestId('profile-form-submit'))
    await waitFor(() => {
      expect(screen.getByTestId('profile-name-error')).toBeInTheDocument()
    })
  })

  it('submits create form successfully', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    mockAxios.post.mockResolvedValueOnce({
      data: {
        data: {
          type: 'profiles',
          id: '3',
          attributes: { name: 'New Profile', description: 'Test', isSystem: false },
        },
      },
    })
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('new-profile-button'))
    await user.type(screen.getByTestId('profile-name-input'), 'New Profile')
    await user.type(screen.getByTestId('profile-description-input'), 'Test description')
    await user.click(screen.getByTestId('profile-form-submit'))
    await waitFor(() => {
      expect(mockAxios.post).toHaveBeenCalledWith('/api/profiles', {
        data: {
          type: 'profiles',
          attributes: {
            name: 'New Profile',
            description: 'Test description',
          },
        },
      })
    })
  })

  it('opens edit modal with pre-filled data', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Admin Profile')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('edit-button-1'))
    expect(screen.getByTestId('profile-form-modal')).toBeInTheDocument()
    expect(screen.getByTestId('profile-name-input')).toHaveValue('Admin Profile')
    expect(screen.getByTestId('profile-description-input')).toHaveValue('Full admin access')
  })

  it('calls clone API when clone button is clicked', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    mockAxios.post.mockResolvedValueOnce({
      data: {
        data: {
          type: 'profiles',
          id: '3',
          attributes: { name: 'Standard User (Copy)' },
        },
      },
    })
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('clone-button-0'))
    await waitFor(() => {
      expect(mockAxios.post).toHaveBeenCalledWith('/api/profiles/1/clone', {
        data: {
          type: 'clone',
          attributes: {},
        },
      })
    })
  })

  it('opens delete confirmation dialog', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Admin Profile')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('delete-button-1'))
    await waitFor(() => {
      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
    })
  })

  it('navigates to profile detail page on row click', async () => {
    const user = userEvent.setup()
    mockAxios.get.mockResolvedValueOnce({ data: mockProfiles })
    render(<ProfilesPage />, { wrapper: createTestWrapper() })
    await waitFor(() => {
      expect(screen.getByText('Standard User')).toBeInTheDocument()
    })
    await user.click(screen.getByTestId('profile-row-0'))
    expect(mockNavigate).toHaveBeenCalledWith('/test-tenant/profiles/1')
  })
})
