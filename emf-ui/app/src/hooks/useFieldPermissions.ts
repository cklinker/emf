/**
 * useFieldPermissions Hook
 *
 * Returns field-level visibility for each field in a collection.
 * Fetches the current user's effective field permissions from the
 * control plane. Falls back to all-visible defaults when the
 * endpoint is unavailable.
 *
 * Field visibility values:
 * - VISIBLE: field is shown and editable
 * - READ_ONLY: field is shown but cannot be edited
 * - HIDDEN: field is not shown at all
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import type { ApiClient } from '@/services/apiClient'

/**
 * Field visibility level.
 */
export type FieldVisibility = 'VISIBLE' | 'READ_ONLY' | 'HIDDEN'

/**
 * Map of field name → visibility level.
 */
export type FieldPermissionMap = Map<string, FieldVisibility>

export interface UseFieldPermissionsReturn {
  /** Map of fieldName → visibility. Fields not in the map default to VISIBLE. */
  fieldPermissions: FieldPermissionMap
  /** Get visibility for a specific field (defaults to VISIBLE if not in map). */
  getFieldVisibility: (fieldName: string) => FieldVisibility
  /** Check if a field should be shown (not HIDDEN). */
  isFieldVisible: (fieldName: string) => boolean
  /** Check if a field is editable (VISIBLE, not READ_ONLY or HIDDEN). */
  isFieldEditable: (fieldName: string) => boolean
  isLoading: boolean
  error: Error | null
}

interface FieldPermissionResponse {
  fieldId: string
  fieldName: string
  visibility: FieldVisibility
}

/**
 * Fetch effective field permissions for the current user on a collection.
 */
async function fetchFieldPermissions(
  apiClient: ApiClient,
  collectionName: string
): Promise<FieldPermissionResponse[]> {
  try {
    const response = await apiClient.get<FieldPermissionResponse[]>(
      `/control/my-permissions/fields/${encodeURIComponent(collectionName)}`
    )
    return Array.isArray(response) ? response : []
  } catch {
    // Endpoint not yet implemented — return empty (all fields default to VISIBLE)
    return []
  }
}

/**
 * Hook to fetch effective field permissions for a collection.
 *
 * @param collectionName - The collection API name
 * @returns Field permission map and helper functions
 */
export function useFieldPermissions(collectionName: string | undefined): UseFieldPermissionsReturn {
  const { apiClient } = useApi()

  const { data, isLoading, error } = useQuery({
    queryKey: ['field-permissions', collectionName],
    queryFn: () => fetchFieldPermissions(apiClient, collectionName!),
    enabled: !!collectionName,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: false,
  })

  const fieldPermissions = useMemo(() => {
    const map = new Map<string, FieldVisibility>()
    if (data) {
      for (const entry of data) {
        map.set(entry.fieldName, entry.visibility)
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
    fieldPermissions,
    getFieldVisibility,
    isFieldVisible,
    isFieldEditable,
    isLoading,
    error: error as Error | null,
  }
}
