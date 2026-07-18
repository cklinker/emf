/**
 * `usePicklistOptions` (slice 2f) — extracts the `ObjectFormPage` picklist-source resolution
 * (FIELD vs GLOBAL via `fieldTypeConfig.globalPicklistId`) + the `/api/picklist-values?filter[…]`
 * fetch into one shared hook so `DropdownInput`, `MultiPicklistInput`, and the `form` field-renderer
 * registry all share a single implementation (no duplication across the three sites).
 *
 * `fieldTypeConfig` may arrive as a parsed object OR a JSON string; `globalPicklistId` (or the
 * legacy pre-#1222 `picklistSourceId` + `picklistSourceType: 'GLOBAL'` dialect) → `GLOBAL` source,
 * else the field id → `FIELD`. Active values are kept and sorted by `sortOrder`. Errors fall back
 * to an empty list. `ObjectFormPage`/`ResourceFormPage` share `resolvePicklistSource` directly.
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

/**
 * Shape of `fieldTypeConfig` for picklist-typed fields. Modern writes use
 * `globalPicklistId`; fields written by the MCP admin tooling before #1222 carry
 * `picklistSourceId` + `picklistSourceType: 'GLOBAL'` instead — both dialects
 * must resolve or the field renders unbound.
 */
interface PicklistFieldTypeConfig {
  globalPicklistId?: string
  picklistSourceId?: string
  picklistSourceType?: string
}

/**
 * Extract the global-picklist id from a picklist field's `fieldTypeConfig`,
 * accepting the legacy `picklistSourceId`/`picklistSourceType` dialect.
 * The config may arrive as a parsed object (JSONB column) or a JSON string.
 */
export function resolveGlobalPicklistId(rawConfig: unknown): string | undefined {
  let config: PicklistFieldTypeConfig | null = null
  if (typeof rawConfig === 'string') {
    try {
      config = JSON.parse(rawConfig) as PicklistFieldTypeConfig
    } catch {
      /* ignore malformed config */
    }
  } else if (rawConfig && typeof rawConfig === 'object') {
    config = rawConfig as PicklistFieldTypeConfig
  }
  return (
    config?.globalPicklistId ??
    (config?.picklistSourceType === 'GLOBAL' ? config?.picklistSourceId : undefined)
  )
}

/** Resolve the `{ sourceId, sourceType }` for a picklist/multi_picklist field (FIELD vs GLOBAL). */
export function resolvePicklistSource(field: Pick<FieldDefinition, 'id' | 'fieldTypeConfig'>): {
  sourceId: string
  sourceType: 'FIELD' | 'GLOBAL'
} {
  const globalPicklistId = resolveGlobalPicklistId(field.fieldTypeConfig)
  if (globalPicklistId) {
    return { sourceId: globalPicklistId, sourceType: 'GLOBAL' }
  }
  return { sourceId: field.id, sourceType: 'FIELD' }
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
          `/api/picklist-values?filter[picklistSourceId][eq]=${encodeURIComponent(sourceId)}&filter[picklistSourceType][eq]=${sourceType}&page[size]=200`
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
