/**
 * useObjectPermissions Hook
 *
 * Returns CRUD permission flags for a given collection. Reads from the shared
 * /api/me/permissions cache populated by useSystemPermissions.
 *
 * Collections not present in the response default to all-permitted so the UI
 * degrades gracefully (the gateway still enforces at the API level).
 */

import { useQueryClient } from '@tanstack/react-query'
import type { MyPermissionsResponse } from './useSystemPermissions'
import { MY_PERMISSIONS_QUERY_KEY } from './useSystemPermissions'

/**
 * Object-level CRUD permission flags.
 */
export interface ObjectPermissions {
  canCreate: boolean
  canRead: boolean
  canEdit: boolean
  canDelete: boolean
}

export interface UseObjectPermissionsReturn {
  permissions: ObjectPermissions
  isLoading: boolean
  error: Error | null
}

/** Default permissive permissions (used when collection has no explicit permissions) */
const PERMISSIVE_DEFAULTS: ObjectPermissions = {
  canCreate: true,
  canRead: true,
  canEdit: true,
  canDelete: true,
}

/**
 * Hook to fetch effective object permissions for a collection.
 * Reads from the shared my-permissions cache.
 *
 * @param collectionName - The collection API name
 * @returns Permission flags, loading state, and error
 */
export function useObjectPermissions(
  collectionName: string | undefined
): UseObjectPermissionsReturn {
  const queryClient = useQueryClient()
  const cached = queryClient.getQueryData<MyPermissionsResponse>(MY_PERMISSIONS_QUERY_KEY)

  const permissions =
    collectionName && cached?.objectPermissions?.[collectionName]
      ? cached.objectPermissions[collectionName]
      : PERMISSIVE_DEFAULTS

  return {
    permissions,
    isLoading: !cached,
    error: null,
  }
}
