/**
 * useLookupDisplayMap Hook
 *
 * Resolves display labels for reference/lookup/master_detail field values.
 * Builds a map of { fieldName: { recordId: displayLabel } } that can be
 * passed to DetailSection, ObjectDataTable, or FieldRenderer to show
 * human-readable names instead of raw UUIDs.
 *
 * Usage:
 *   const { lookupDisplayMap } = useLookupDisplayMap(fields)
 *   <DetailSection ... lookupDisplayMap={lookupDisplayMap} />
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import type { FieldDefinition } from './useCollectionSchema'

/** Reference field types that should have their display values resolved */
const REFERENCE_TYPES = new Set(['master_detail', 'lookup', 'reference'])

export interface UseLookupDisplayMapResult {
  /** Map of { fieldName: { recordId: displayLabel } } */
  lookupDisplayMap: Record<string, Record<string, string>> | undefined
  /** Map of { fieldName: targetCollectionName } for building links */
  lookupTargetNameMap: Record<string, string> | undefined
  /** Whether the lookup data is still loading */
  isLoading: boolean
}

/**
 * Hook to resolve display labels for reference/lookup/master_detail fields.
 *
 * @param fields - Array of field definitions from the collection schema
 * @param queryKeyPrefix - Optional prefix for the query key (for cache isolation)
 * @returns Display map, target name map, and loading state
 */
export function useLookupDisplayMap(
  fields: FieldDefinition[] | undefined,
  queryKeyPrefix?: string
): UseLookupDisplayMapResult {
  const { apiClient } = useApi()

  // Identify reference fields that need display label resolution
  const lookupFields = useMemo(() => {
    if (!fields) return []
    return fields.filter((f) => REFERENCE_TYPES.has(f.type) && f.referenceCollectionId)
  }, [fields])

  // Stable query key based on field IDs
  const fieldIds = useMemo(() => lookupFields.map((f) => f.id), [lookupFields])

  const { data: lookupData, isLoading } = useQuery({
    queryKey: [queryKeyPrefix ?? 'lookup-display-map', fieldIds],
    staleTime: 2 * 60 * 1000, // 2 minutes — lookup records change infrequently
    queryFn: async () => {
      const displayMap: Record<string, Record<string, string>> = {}
      const targetNameMap: Record<string, string> = {}

      // Group fields by referenceCollectionId to minimize API calls
      const targetGroupMap = new Map<string, FieldDefinition[]>()
      for (const field of lookupFields) {
        const target = field.referenceCollectionId!
        if (!targetGroupMap.has(target)) {
          targetGroupMap.set(target, [])
        }
        targetGroupMap.get(target)!.push(field)
      }

      await Promise.all(
        Array.from(targetGroupMap.entries()).map(async ([targetCollectionId, groupFields]) => {
          try {
            // Fetch target collection schema to determine display field and name
            const targetSchema = await apiClient.get<{
              name: string
              displayFieldName?: string
              fields?: Array<{ name: string; type: string }>
            }>(`/control/collections/${targetCollectionId}`)
            const targetName = targetSchema.name

            // Track target collection name for each field (useful for building links)
            for (const field of groupFields) {
              targetNameMap[field.name] = targetName
            }

            // Determine the best display field: explicit > "name" > first string > id
            let displayFieldName = 'id'
            if (targetSchema.displayFieldName) {
              displayFieldName = targetSchema.displayFieldName
            } else if (targetSchema.fields) {
              const nameField = targetSchema.fields.find((f) => f.name.toLowerCase() === 'name')
              if (nameField) {
                displayFieldName = nameField.name
              } else {
                const firstStringField = targetSchema.fields.find(
                  (f) => f.type.toUpperCase() === 'STRING'
                )
                if (firstStringField) {
                  displayFieldName = firstStringField.name
                }
              }
            }

            // Fetch records from the target collection
            const recordsResponse = await apiClient.get<Record<string, unknown>>(
              `/api/${targetName}?page[size]=200`
            )
            const data = recordsResponse?.data
            const records: Array<Record<string, unknown>> = Array.isArray(data) ? data : []

            // Build id → label map
            const idToLabel: Record<string, string> = {}
            for (const record of records) {
              const attrs = (record.attributes || record) as Record<string, unknown>
              const id = String(record.id || attrs.id || '')
              const label = attrs[displayFieldName] ? String(attrs[displayFieldName]) : id
              idToLabel[id] = label
            }

            // Assign to all fields targeting this collection
            for (const field of groupFields) {
              displayMap[field.name] = idToLabel
            }
          } catch {
            // On error, set empty maps so fields fall back to showing IDs
            for (const field of groupFields) {
              displayMap[field.name] = {}
            }
          }
        })
      )

      return { displayMap, targetNameMap }
    },
    enabled: lookupFields.length > 0,
  })

  return {
    lookupDisplayMap: lookupData?.displayMap,
    lookupTargetNameMap: lookupData?.targetNameMap,
    isLoading,
  }
}
