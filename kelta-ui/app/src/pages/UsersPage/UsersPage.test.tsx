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
const adminUsersCreate = vi.fn()
const profilesList = vi.fn()
const delegatedUsersList = vi.fn()
const delegatedUsersCreate = vi.fn()
const delegatedUsersUpdate = vi.fn()

vi.mock('../../context/ApiContext', () => ({
  useApi: () => ({
    keltaClient: {
      admin: {
        users: {
          list: adminUsersList,
          create: adminUsersCreate,
          activate: vi.fn(),
          deactivate: vi.fn(),
        },
        profiles: { list: profilesList },
        delegated: {
          users: {
            list: delegatedUsersList,
            create: delegatedUsersCreate,
            update: delegatedUsersUpdate,
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
}

const NONE_SUMMARY: DelegatedAdminSummary = {
  delegated: false,
  canCreateUsers: false,
  canDeactivateUsers: false,
  canResetPasswords: false,
  manageableProfiles: [],
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
    adminUsersCreate.mockReset().mockResolvedValue(ADMIN_USER)
    profilesList.mockReset().mockResolvedValue([
      { id: 'p1', name: 'Standard User' },
      { id: 'p2', name: 'System Administrator' },
    ])
    delegatedUsersList.mockReset().mockResolvedValue([])
    delegatedUsersCreate.mockReset().mockResolvedValue(DELEGATED_USER)
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

    it("offers the tenant's profiles in the create form", async () => {
      renderPage()
      await waitFor(() => expect(profilesList).toHaveBeenCalled())

      fireEvent.click(screen.getByText('Create User'))

      const select = (await screen.findByTestId('create-profile-select')) as HTMLSelectElement
      await waitFor(() =>
        expect(Array.from(select.options).map((o) => o.textContent)).toEqual(
          expect.arrayContaining(['Standard User', 'System Administrator'])
        )
      )
    })

    it('blocks submit until a profile is chosen, then creates with the profileId', async () => {
      renderPage()
      await waitFor(() => expect(adminUsersList).toHaveBeenCalled())

      fireEvent.click(screen.getByText('Create User'))
      fireEvent.change(await screen.findByLabelText(/Email/), {
        target: { value: 'new@example.com' },
      })
      fireEvent.change(screen.getByLabelText(/First Name/), { target: { value: 'New' } })
      fireEvent.change(screen.getByLabelText(/Last Name/), { target: { value: 'User' } })

      // No profile chosen — the submit is rejected client-side, never hitting the API.
      fireEvent.click(screen.getByText('Create'))
      expect(await screen.findByText('Profile is required')).toBeInTheDocument()
      expect(adminUsersCreate).not.toHaveBeenCalled()

      fireEvent.change(screen.getByTestId('create-profile-select'), { target: { value: 'p2' } })
      fireEvent.click(screen.getByText('Create'))
      await waitFor(() =>
        expect(adminUsersCreate).toHaveBeenCalledWith(
          expect.objectContaining({ email: 'new@example.com', profileId: 'p2' })
        )
      )
    })
  })
})
