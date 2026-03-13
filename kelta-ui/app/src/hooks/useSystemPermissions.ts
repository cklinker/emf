import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../context'
import { useApi } from '../context/ApiContext'

/**
 * System-level permission flags for the current user.
 */
export type SystemPermissions = Record<string, boolean>

export interface UseSystemPermissionsReturn {
  permissions: SystemPermissions
  hasPermission: (permission: string) => boolean
  isLoading: boolean
  error: Error | null
}

/**
 * Hook that fetches the current user's system-level permissions
 * from the `/api/me/permissions` endpoint.
 * Results are cached with a 5-minute stale time.
 */
export function useSystemPermissions(): UseSystemPermissionsReturn {
  const { user } = useAuth()
  const { keltaClient } = useApi()

  const { data, isLoading, error } = useQuery<SystemPermissions>({
    queryKey: ['my-permissions', 'system'],
    queryFn: async () => {
      const response = await keltaClient.getAxiosInstance().get('/api/me/permissions')
      return (response.data?.systemPermissions ?? {}) as SystemPermissions
    },
    enabled: !!user,
    staleTime: 5 * 60 * 1000,
    retry: 2,
    retryDelay: 1000,
  })

  const permissions: SystemPermissions = data ?? {}

  const hasPermission = (permission: string): boolean => {
    return permissions[permission] === true
  }

  return {
    permissions,
    hasPermission,
    isLoading,
    error: error as Error | null,
  }
}
