/**
 * RequireUserManagementAccess tests — the Users-page gate passes for MANAGE_USERS
 * holders OR delegated admins, blocks everyone else, and stays permissive while loading.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { RequireUserManagementAccess } from './RequireUserManagementAccess'

const hasPermission = vi.fn()
let permissionsLoading = false
let permissionsError: Error | null = null
vi.mock('../../hooks/useSystemPermissions', () => ({
  useSystemPermissions: () => ({
    hasPermission,
    isLoading: permissionsLoading,
    error: permissionsError,
  }),
}))

let isDelegated = false
let delegatedLoading = false
vi.mock('../../hooks/useDelegatedAdmin', () => ({
  useDelegatedAdmin: () => ({ isDelegated, isLoading: delegatedLoading }),
}))

function renderGate() {
  return render(
    <RequireUserManagementAccess>
      <div data-testid="protected-child">Users list</div>
    </RequireUserManagementAccess>
  )
}

describe('RequireUserManagementAccess', () => {
  beforeEach(() => {
    hasPermission.mockReset().mockReturnValue(false)
    permissionsLoading = false
    permissionsError = null
    isDelegated = false
    delegatedLoading = false
  })

  it('renders children when MANAGE_USERS is granted', () => {
    hasPermission.mockReturnValue(true)
    renderGate()
    expect(screen.getByTestId('protected-child')).toBeInTheDocument()
  })

  it('renders children for a delegated admin without MANAGE_USERS', () => {
    hasPermission.mockReturnValue(false)
    isDelegated = true
    renderGate()
    expect(screen.getByTestId('protected-child')).toBeInTheDocument()
  })

  it('shows the insufficient-permissions message when neither applies', () => {
    hasPermission.mockReturnValue(false)
    isDelegated = false
    renderGate()
    expect(screen.getByText('Insufficient permissions')).toBeInTheDocument()
    expect(screen.queryByTestId('protected-child')).not.toBeInTheDocument()
  })

  it('renders children while permissions are loading (permissive)', () => {
    permissionsLoading = true
    renderGate()
    expect(screen.getByTestId('protected-child')).toBeInTheDocument()
  })
})
