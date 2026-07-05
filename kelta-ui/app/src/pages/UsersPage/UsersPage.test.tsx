/**
 * UsersPage tests — the delegated (scoped) path routes all reads/writes through
 * `keltaClient.admin.delegated.*`, gates create/deactivate on the caller's summary,
 * and limits the create-form profile choices; the full-admin path uses `admin.users.*`.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import type { DelegatedAdminSummary } from '@kelta/sdk'
import { UsersPage } from './UsersPage'
import { I18nProvider } from '../../context/I18nContext'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../../context/TenantContext', () => ({ getTenantSlug: () => 't' }))

const hasPermission = vi.fn()
vi.mock('../../hooks/useSystemPermissions', () => ({
  useSystemPermissions: () => ({ hasPermission, isLoading: false }),
}))

let delegatedReturn: { isDelegated: boolean; summary: DelegatedAdminSummary | undefined }
vi.mock('../../hooks/useDelegatedAdmin', () => ({
  useDelegatedAdmin: () => delegatedReturn,
}))

vi.mock('../../components/Toast', () => ({ useToast: () => ({ showToast: vi.fn() }) }))

const adminUsersList = vi.fn()
const delegatedUsersList = vi.fn()
const delegatedUsersCreate = vi.fn()
const delegatedUsersUpdate = vi.fn()
const delegatedUsersListPermissionSets = vi.fn()
const delegatedUsersAssignPermissionSet = vi.fn()
const delegatedUsersRemovePermissionSet = vi.fn()

vi.mock('../../context/ApiContext', () => ({
  useApi: () => ({
    keltaClient: {
      admin: {
        users: { list: adminUsersList, create: vi.fn(), activate: vi.fn(), deactivate: vi.fn() },
        delegated: {
          users: {
            list: delegatedUsersList,
            create: delegatedUsersCreate,
            update: delegatedUsersUpdate,
            listPermissionSets: delegatedUsersListPermissionSets,
            assignPermissionSet: delegatedUsersAssignPermissionSet,
            removePermissionSet: delegatedUsersRemovePermissionSet,
          },
        },
      },
    },
  }),
}))

const SCOPED_SUMMARY: DelegatedAdminSummary = {
  delegated: true,
  canCreateUsers: true,
  canDeactivateUsers: false,
  canResetPasswords: false,
  manageableProfiles: [{ id: 'p1', name: 'Standard User' }],
  assignablePermissionSets: [],
}

const NONE_SUMMARY: DelegatedAdminSummary = {
  delegated: false,
  canCreateUsers: false,
  canDeactivateUsers: false,
  canResetPasswords: false,
  manageableProfiles: [],
  assignablePermissionSets: [],
}

const DELEGATED_USER = {
  id: 'du1',
  email: 'scoped@example.com',
  firstName: 'Scoped',
  lastName: 'User',
  username: 'scoped',
  status: 'ACTIVE',
  locale: 'en',
  timezone: 'UTC',
  loginCount: 0,
  mfaEnabled: false,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const ADMIN_USER = {
  id: 'au1',
  email: 'admin-listed@example.com',
  firstName: 'Full',
  lastName: 'Listed',
  username: 'flisted',
  status: 'ACTIVE',
  locale: 'en',
  timezone: 'UTC',
  loginCount: 0,
  mfaEnabled: false,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <MemoryRouter initialEntries={['/t/users']}>
          <UsersPage />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>
  )
}

describe('UsersPage', () => {
  beforeEach(() => {
    mockNavigate.mockClear()
    hasPermission.mockReset()
    adminUsersList.mockReset().mockResolvedValue({ content: [], totalPages: 0 })
    delegatedUsersList.mockReset().mockResolvedValue([])
    delegatedUsersCreate.mockReset().mockResolvedValue(DELEGATED_USER)
    delegatedUsersListPermissionSets.mockReset().mockResolvedValue([])
  })

  describe('delegated (scoped) mode', () => {
    beforeEach(() => {
      hasPermission.mockReturnValue(false) // no MANAGE_USERS
      delegatedReturn = { isDelegated: true, summary: SCOPED_SUMMARY }
      delegatedUsersList.mockResolvedValue([DELEGATED_USER])
    })

    it('reads through delegated.users.list, not admin.users.list', async () => {
      renderPage()
      await waitFor(() => expect(delegatedUsersList).toHaveBeenCalled())
      expect(adminUsersList).not.toHaveBeenCalled()
      expect(await screen.findByText('Scoped User')).toBeInTheDocument()
    })

    it('shows the delegated-scope badge', async () => {
      renderPage()
      await waitFor(() => expect(delegatedUsersList).toHaveBeenCalled())
      expect(screen.getByTestId('delegated-scope-badge')).toBeInTheDocument()
    })

    it("limits the create-form profile select to the caller's manageable profiles", async () => {
      renderPage()
      await waitFor(() => expect(delegatedUsersList).toHaveBeenCalled())

      // Create button is present because canCreateUsers=true.
      fireEvent.click(screen.getByText('Create User'))

      const select = (await screen.findByTestId('create-profile-select')) as HTMLSelectElement
      const optionLabels = Array.from(select.options).map((o) => o.textContent)
      // "Select..." placeholder + the single manageable profile, nothing else.
      expect(optionLabels).toContain('Standard User')
      expect(optionLabels).not.toContain('Admin')
      expect(select.querySelectorAll('option[value="p1"]')).toHaveLength(1)
    })

    it('hides the Deactivate action when canDeactivateUsers=false', async () => {
      renderPage()
      // Seed an ACTIVE user; with canDeactivateUsers=false no Deactivate button should render.
      expect(await screen.findByText('Scoped User')).toBeInTheDocument()
      expect(screen.queryByText('Deactivate')).not.toBeInTheDocument()
    })
  })

  describe('full-admin (non-scoped) mode', () => {
    beforeEach(() => {
      hasPermission.mockReturnValue(true) // MANAGE_USERS granted
      delegatedReturn = { isDelegated: false, summary: NONE_SUMMARY }
      adminUsersList.mockResolvedValue({ content: [ADMIN_USER], totalPages: 1 })
    })

    it('reads through admin.users.list, not delegated.users.list', async () => {
      renderPage()
      await waitFor(() => expect(adminUsersList).toHaveBeenCalled())
      expect(delegatedUsersList).not.toHaveBeenCalled()
      expect(await screen.findByText('Full Listed')).toBeInTheDocument()
      // No delegated badge in full-admin mode.
      expect(screen.queryByTestId('delegated-scope-badge')).not.toBeInTheDocument()
    })
  })
})
