/**
 * `usePicklistOptions` (slice 2f) — extracts the `ObjectFormPage` picklist-source resolution
 * (FIELD vs GLOBAL via `fieldTypeConfig.globalPicklistId`) + the `/api/picklist-values?filter[…]`
 * fetch into one shared hook so `DropdownInput`, `MultiPicklistInput`, and the `form` field-renderer
 * registry all share a single implementation (no duplication across the three sites).
 *
 * The resolution mirrors `ObjectFormPage.tsx` ~838-865 exactly: `fieldTypeConfig` may arrive as a
 * parsed object OR a JSON string; `globalPicklistId` → `GLOBAL` source, else the field id → `FIELD`.
 * Active values are kept and sorted by `sortOrder`. Errors fall back to an empty list.
 */
import { useQuery } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'

/** Picklist value returned from the API (field names match the backend schema). */
interface PicklistValueDto {
  value: string
  label: string
  isDefault: boolean
  isActive: boolean
  sortOrder: number
}

/** Resolve the `{ sourceId, sourceType }` for a picklist/multi_picklist field (FIELD vs GLOBAL). */
export function resolvePicklistSource(field: Pick<FieldDefinition, 'id' | 'fieldTypeConfig'>): {
  sourceId: string
  sourceType: 'FIELD' | 'GLOBAL'
} {
  let sourceId = field.id
  let sourceType: 'FIELD' | 'GLOBAL' = 'FIELD'

  // fieldTypeConfig may arrive as a parsed object (JSONB column) or a JSON string. Handle both.
  const rawConfig = field.fieldTypeConfig
  let config: { globalPicklistId?: string } | null = null
  if (typeof rawConfig === 'string') {
    try {
      config = JSON.parse(rawConfig) as { globalPicklistId?: string }
    } catch {
      /* ignore malformed config */
    }
  } else if (rawConfig && typeof rawConfig === 'object') {
    config = rawConfig as { globalPicklistId?: string }
  }
  if (config?.globalPicklistId) {
    sourceId = config.globalPicklistId
    sourceType = 'GLOBAL'
  }
  return { sourceId, sourceType }
}

export interface UsePicklistOptionsResult {
  options: string[]
  isLoading: boolean
}

/**
 * Fetch the active, sorted picklist `value[]` for one picklist/multi_picklist field.
 * Disabled when no field is supplied or when `enabled` is false (e.g. editor mode).
 */
export function usePicklistOptions(
  field: Pick<FieldDefinition, 'id' | 'fieldTypeConfig'> | undefined,
  enabled = true
): UsePicklistOptionsResult {
  const { apiClient } = useApi()
  const { data, isLoading } = useQuery({
    queryKey: ['page-input-picklist', field?.id, field?.fieldTypeConfig],
    queryFn: async () => {
      if (!field) return []
      try {
        const { sourceId, sourceType } = resolvePicklistSource(field)
        const values = await apiClient.getList<PicklistValueDto>(
          `/api/picklist-values?filter[picklistSourceId][eq]=${encodeURIComponent(sourceId)}&filter[picklistSourceType][eq]=${sourceType}`
        )
        return values
          .filter((v) => v.isActive)
          .sort((a, b) => a.sortOrder - b.sortOrder)
          .map((v) => v.value)
      } catch {
        return []
      }
    },
    enabled: enabled && !!field,
    staleTime: 5 * 60 * 1000,
  })
  return { options: data ?? [], isLoading }
}
