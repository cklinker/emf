/**
 * useFieldPermissions Hook
 *
 * Returns field-level visibility for each field in a collection.
 * Reads from the shared /api/me/permissions cache populated by
 * useSystemPermissions.
 *
 * Field visibility values:
 * - VISIBLE: field is shown and editable
 * - READ_ONLY: field is shown but cannot be edited
 * - HIDDEN: field is not shown at all
 */

import { useMemo } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { MyPermissionsResponse } from './useSystemPermissions'
import { MY_PERMISSIONS_QUERY_KEY } from './useSystemPermissions'

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

/**
 * Hook to fetch effective field permissions for a collection.
 * Reads from the shared my-permissions cache.
 *
 * @param collectionName - The collection API name
 * @returns Field permission map and helper functions
 */
export function useFieldPermissions(collectionName: string | undefined): UseFieldPermissionsReturn {
  const queryClient = useQueryClient()
  const cached = queryClient.getQueryData<MyPermissionsResponse>(MY_PERMISSIONS_QUERY_KEY)

  const rawFieldPerms =
    collectionName && cached?.fieldPermissions?.[collectionName]
      ? cached.fieldPermissions[collectionName]
      : undefined

  const fieldPermissions = useMemo(() => {
    const map = new Map<string, FieldVisibility>()
    if (rawFieldPerms) {
      for (const [fieldName, visibility] of Object.entries(rawFieldPerms)) {
        map.set(fieldName, visibility as FieldVisibility)
      }
    }
    return map
  }, [rawFieldPerms])

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
    isLoading: !cached,
    error: null,
  }
}
