import { useQuery } from '@tanstack/react-query'
import type { DelegatedAdminSummary } from '@kelta/sdk'
import { useAuth } from '../context'
import { useApi } from '../context/ApiContext'
import { useSystemPermissions } from './useSystemPermissions'

export interface UseDelegatedAdminReturn {
  summary: DelegatedAdminSummary | undefined
  isDelegated: boolean
  isLoading: boolean
  error: Error | null
}

const NONE: DelegatedAdminSummary = {
  delegated: false,
  canCreateUsers: false,
  canDeactivateUsers: false,
  canResetPasswords: false,
  manageableProfiles: [],
}

/**
 * Fetches the caller's delegated-administration summary from
 * `GET /api/admin/delegated/me`. Only queried for authenticated users who do
 * NOT hold `MANAGE_USERS` — full admins never need the delegated path.
 */
export function useDelegatedAdmin(): UseDelegatedAdminReturn {
  const { user } = useAuth()
  const { keltaClient } = useApi()
  const { hasPermission, isLoading: permissionsLoading } = useSystemPermissions()

  const isFullAdmin = hasPermission('MANAGE_USERS')

  const { data, isLoading, error } = useQuery<DelegatedAdminSummary>({
    queryKey: ['delegated-admin-me'],
    queryFn: () => keltaClient.admin.delegated.me(),
    enabled: !!user && !permissionsLoading && !isFullAdmin,
    staleTime: 60 * 1000,
    retry: 1,
  })

  const summary = isFullAdmin ? NONE : data
  return {
    summary,
    isDelegated: summary?.delegated === true,
    isLoading: permissionsLoading || (!isFullAdmin && isLoading),
    error: error as Error | null,
  }
}
