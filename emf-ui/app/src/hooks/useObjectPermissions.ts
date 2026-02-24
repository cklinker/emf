/**
 * useObjectPermissions Hook
 *
 * Returns CRUD permission flags for a given collection. Returns permissive
 * defaults since the permissions endpoint is not yet available via JSON:API.
 * The UI degrades gracefully with all permissions allowed.
 *
 * The hook uses React Query with a long stale time (5 min) since permissions
 * change infrequently.
 */

import { useQuery } from '@tanstack/react-query'

/**
 * Object-level CRUD permission flags.
 */
export interface ObjectPermissions {
  canCreate: boolean
  canRead: boolean
  canEdit: boolean
  canDelete: boolean
  canViewAll: boolean
  canModifyAll: boolean
}

export interface UseObjectPermissionsReturn {
  permissions: ObjectPermissions
  isLoading: boolean
  error: Error | null
}

/** Default permissive permissions (used when backend hasn't implemented the endpoint) */
const PERMISSIVE_DEFAULTS: ObjectPermissions = {
  canCreate: true,
  canRead: true,
  canEdit: true,
  canDelete: true,
  canViewAll: true,
  canModifyAll: true,
}

/**
 * Return permissive object permissions.
 * Permissions endpoint is not yet available via JSON:API — fall back
 * to permissive defaults so the UI works without the permission backend.
 */
async function fetchObjectPermissions(): Promise<ObjectPermissions> {
  // Permissions are not yet available via JSON:API — return permissive defaults
  return PERMISSIVE_DEFAULTS
}

/**
 * Hook to fetch effective object permissions for a collection.
 *
 * @param collectionName - The collection API name
 * @returns Permission flags, loading state, and error
 */
export function useObjectPermissions(
  collectionName: string | undefined
): UseObjectPermissionsReturn {
  const { data, isLoading, error } = useQuery({
    queryKey: ['object-permissions', collectionName],
    queryFn: () => fetchObjectPermissions(),
    enabled: !!collectionName,
    staleTime: 5 * 60 * 1000, // 5 minutes — permissions change rarely
    retry: false, // Don't retry on failure (likely 404)
  })

  return {
    permissions: data ?? PERMISSIVE_DEFAULTS,
    isLoading,
    error: error as Error | null,
  }
}
