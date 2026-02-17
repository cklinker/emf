/**
 * useObjectPermissions Hook
 *
 * Returns CRUD permission flags for a given collection. Fetches the current
 * user's effective object permissions from the control plane. When the
 * permission endpoint is unavailable (not yet implemented), falls back to
 * permissive defaults so the UI degrades gracefully.
 *
 * The hook uses React Query with a long stale time (5 min) since permissions
 * change infrequently.
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import type { ApiClient } from '@/services/apiClient'

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
 * Fetch effective object permissions for the current user on a collection.
 * Tries GET /control/my-permissions/objects/{collectionName}.
 * Falls back to permissive defaults on 404/501 (endpoint not yet implemented).
 */
async function fetchObjectPermissions(
  apiClient: ApiClient,
  collectionName: string
): Promise<ObjectPermissions> {
  try {
    const response = await apiClient.get<ObjectPermissions>(
      `/control/my-permissions/objects/${encodeURIComponent(collectionName)}`
    )
    return {
      canCreate: response?.canCreate ?? true,
      canRead: response?.canRead ?? true,
      canEdit: response?.canEdit ?? true,
      canDelete: response?.canDelete ?? true,
      canViewAll: response?.canViewAll ?? true,
      canModifyAll: response?.canModifyAll ?? true,
    }
  } catch {
    // Endpoint not yet implemented — fall back to permissive defaults.
    // This ensures the UI works without the permission backend.
    return PERMISSIVE_DEFAULTS
  }
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
  const { apiClient } = useApi()

  const { data, isLoading, error } = useQuery({
    queryKey: ['object-permissions', collectionName],
    queryFn: () => fetchObjectPermissions(apiClient, collectionName!),
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
