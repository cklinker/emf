/**
 * useCollectionPermissions Hook
 *
 * Fetches both object-level CRUD permissions and field-level visibility
 * for a collection in a single API call. This replaces the pattern of
 * calling useObjectPermissions + useFieldPermissions separately, which
 * made 2 HTTP requests per page load.
 *
 * The individual hooks (useObjectPermissions, useFieldPermissions) still
 * exist for backward compatibility but app route pages should prefer
 * this combined hook.
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import type { ApiClient } from '@/services/apiClient'
import type { ObjectPermissions } from './useObjectPermissions'

/**
 * Field visibility level.
 */
export type FieldVisibility = 'VISIBLE' | 'READ_ONLY' | 'HIDDEN'

/** Combined response from the backend */
interface CollectionPermissionsResponse {
  objectPermissions: ObjectPermissions
  fieldPermissions: Record<string, string>
}

export interface UseCollectionPermissionsReturn {
  /** Object-level CRUD permission flags */
  permissions: ObjectPermissions
  /** Map of fieldName → visibility. Fields not in the map default to VISIBLE. */
  fieldPermissions: Map<string, FieldVisibility>
  /** Get visibility for a specific field (defaults to VISIBLE if not in map). */
  getFieldVisibility: (fieldName: string) => FieldVisibility
  /** Check if a field should be shown (not HIDDEN). */
  isFieldVisible: (fieldName: string) => boolean
  /** Check if a field is editable (VISIBLE, not READ_ONLY or HIDDEN). */
  isFieldEditable: (fieldName: string) => boolean
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
 * Fetch combined object + field permissions for the current user on a collection.
 */
async function fetchCollectionPermissions(
  apiClient: ApiClient,
  collectionName: string
): Promise<CollectionPermissionsResponse> {
  try {
    const response = await apiClient.get<CollectionPermissionsResponse>(
      `/control/my-permissions/collection/${encodeURIComponent(collectionName)}`
    )
    return {
      objectPermissions: {
        canCreate: response?.objectPermissions?.canCreate ?? true,
        canRead: response?.objectPermissions?.canRead ?? true,
        canEdit: response?.objectPermissions?.canEdit ?? true,
        canDelete: response?.objectPermissions?.canDelete ?? true,
        canViewAll: response?.objectPermissions?.canViewAll ?? true,
        canModifyAll: response?.objectPermissions?.canModifyAll ?? true,
      },
      fieldPermissions: response?.fieldPermissions ?? {},
    }
  } catch {
    // Endpoint not yet implemented — fall back to permissive defaults.
    return {
      objectPermissions: PERMISSIVE_DEFAULTS,
      fieldPermissions: {},
    }
  }
}

/**
 * Hook to fetch combined object + field permissions for a collection.
 * Makes a single API call instead of two separate calls.
 *
 * @param collectionName - The collection API name
 * @returns Combined permission data and helper functions
 */
export function useCollectionPermissions(
  collectionName: string | undefined
): UseCollectionPermissionsReturn {
  const { apiClient } = useApi()

  const { data, isLoading, error } = useQuery({
    queryKey: ['collection-permissions', collectionName],
    queryFn: () => fetchCollectionPermissions(apiClient, collectionName!),
    enabled: !!collectionName,
    staleTime: 5 * 60 * 1000, // 5 minutes — permissions change rarely
    retry: false,
  })

  const fieldPermissions = useMemo(() => {
    const map = new Map<string, FieldVisibility>()
    if (data?.fieldPermissions) {
      for (const [fieldName, visibility] of Object.entries(data.fieldPermissions)) {
        map.set(fieldName, visibility as FieldVisibility)
      }
    }
    return map
  }, [data])

  const getFieldVisibility = useMemo(() => {
    return (fieldName: string): FieldVisibility => {
      return fieldPermissions.get(fieldName) ?? 'VISIBLE'
    }
  }, [fieldPermissions])

  const isFieldVisible = useMemo(() => {
    return (fieldName: string): boolean => {
      return getFieldVisibility(fieldName) !== 'HIDDEN'
    }
  }, [getFieldVisibility])

  const isFieldEditable = useMemo(() => {
    return (fieldName: string): boolean => {
      return getFieldVisibility(fieldName) === 'VISIBLE'
    }
  }, [getFieldVisibility])

  return {
    permissions: data?.objectPermissions ?? PERMISSIVE_DEFAULTS,
    fieldPermissions,
    getFieldVisibility,
    isFieldVisible,
    isFieldEditable,
    isLoading,
    error: error as Error | null,
  }
}
