/**
 * useDelegatedAdmin tests — full admins never hit the delegated endpoint; non-admins
 * fetch their summary and `isDelegated` reflects `summary.delegated`.
 */
import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { DelegatedAdminSummary } from '@kelta/sdk'
import { useDelegatedAdmin } from './useDelegatedAdmin'

const meMock = vi.fn()
vi.mock('../context/ApiContext', () => ({
  useApi: () => ({ keltaClient: { admin: { delegated: { me: meMock } } } }),
}))

vi.mock('../context', () => ({
  useAuth: () => ({ user: { id: 'u1', email: 'u1@example.com' } }),
}))

const hasPermission = vi.fn()
vi.mock('./useSystemPermissions', () => ({
  useSystemPermissions: () => ({ hasPermission, isLoading: false }),
}))

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return React.createElement(QueryClientProvider, { client: queryClient }, children)
}

const DELEGATED_SUMMARY: DelegatedAdminSummary = {
  delegated: true,
  canCreateUsers: true,
  canDeactivateUsers: false,
  canResetPasswords: false,
  manageableProfiles: [{ id: 'p1', name: 'Standard User' }],
}

describe('useDelegatedAdmin', () => {
  beforeEach(() => {
    meMock.mockReset()
    hasPermission.mockReset()
  })

  it('does not call me() for a full admin and reports isDelegated=false', async () => {
    hasPermission.mockReturnValue(true) // MANAGE_USERS granted
    meMock.mockResolvedValue(DELEGATED_SUMMARY)

    const { result } = renderHook(() => useDelegatedAdmin(), { wrapper })

    // Full admins short-circuit to the NONE summary; the endpoint is never queried.
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(meMock).not.toHaveBeenCalled()
    expect(result.current.isDelegated).toBe(false)
    expect(result.current.summary?.delegated).toBe(false)
  })

  it('calls me() for a non-admin and drives isDelegated from summary.delegated', async () => {
    hasPermission.mockReturnValue(false) // no MANAGE_USERS
    meMock.mockResolvedValue(DELEGATED_SUMMARY)

    const { result } = renderHook(() => useDelegatedAdmin(), { wrapper })

    await waitFor(() => expect(result.current.summary).toBeDefined())
    expect(meMock).toHaveBeenCalledTimes(1)
    expect(result.current.isDelegated).toBe(true)
    expect(result.current.summary?.canCreateUsers).toBe(true)
  })

  it('reports isDelegated=false when a non-admin summary is not delegated', async () => {
    hasPermission.mockReturnValue(false)
    meMock.mockResolvedValue({ ...DELEGATED_SUMMARY, delegated: false })

    const { result } = renderHook(() => useDelegatedAdmin(), { wrapper })

    await waitFor(() => expect(result.current.summary).toBeDefined())
    expect(meMock).toHaveBeenCalledTimes(1)
    expect(result.current.isDelegated).toBe(false)
  })
})
