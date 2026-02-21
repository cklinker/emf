import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context'
import { useAuth } from '../context'

/**
 * System-level permission flags for the current user.
 * Fetched from GET /control/my-permissions/system.
 */
export type SystemPermissions = Record<string, boolean>

export interface UseSystemPermissionsReturn {
  permissions: SystemPermissions
  hasPermission: (permission: string) => boolean
  isLoading: boolean
  error: Error | null
}

/**
 * Hook that fetches the current user's system-level permissions.
 * Results are cached with a 5-minute stale time.
 *
 * PLATFORM_ADMIN users receive all permissions as true from the API.
 */
export function useSystemPermissions(): UseSystemPermissionsReturn {
  const { apiClient } = useApi()
  const { user } = useAuth()

  const { data, isLoading, error } = useQuery<SystemPermissions>({
    queryKey: ['my-permissions', 'system'],
    queryFn: async () => {
      try {
        return await apiClient.get<SystemPermissions>('/control/my-permissions/system')
      } catch {
        // Fall back to empty permissions if endpoint unavailable
        return {}
      }
    },
    enabled: !!user,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  const permissions: SystemPermissions = data ?? {}

  const hasPermission = (permission: string): boolean => {
    // PLATFORM_ADMIN role bypasses all permission checks
    if (user?.roles?.includes('PLATFORM_ADMIN')) return true
    return permissions[permission] === true
  }

  return {
    permissions,
    hasPermission,
    isLoading,
    error: error as Error | null,
  }
}
