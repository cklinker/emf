/**
 * useCollectionPermissions Hook
 *
 * Returns both object-level CRUD permissions and field-level visibility
 * for a collection. Returns permissive defaults since the combined
 * permissions endpoint is not yet available via JSON:API.
 *
 * The individual hooks (useObjectPermissions, useFieldPermissions) still
 * exist for backward compatibility but app route pages should prefer
 * this combined hook.
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
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
 * Return permissive combined object + field permissions.
 * Permissions endpoint is not yet available via JSON:API — fall back
 * to permissive defaults so the UI works without the permission backend.
 */
async function fetchCollectionPermissions(): Promise<CollectionPermissionsResponse> {
  // Permissions are not yet available via JSON:API — return permissive defaults
  return {
    objectPermissions: PERMISSIVE_DEFAULTS,
    fieldPermissions: {},
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
  const { data, isLoading, error } = useQuery({
    queryKey: ['collection-permissions', collectionName],
    queryFn: () => fetchCollectionPermissions(),
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
