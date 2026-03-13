import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../context'
import { useTenant } from '../context/TenantContext'

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
  const { user, getAccessToken } = useAuth()
  const { tenantSlug } = useTenant()

  const { data, isLoading, error } = useQuery<SystemPermissions>({
    queryKey: ['my-permissions', 'system', tenantSlug],
    queryFn: async () => {
      const token = await getAccessToken()
      const response = await fetch(`/${tenantSlug}/api/me/permissions`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!response.ok) return {} as SystemPermissions
      const json = await response.json()
      return (json.systemPermissions ?? {}) as SystemPermissions
    },
    enabled: !!user && !!tenantSlug,
    staleTime: 5 * 60 * 1000,
    retry: false,
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
