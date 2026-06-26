/**
 * `useLookupOptions` (slice 2f) — a thin wrapper over the existing `useLookupDisplayMap`
 * (`@/hooks/useLookupDisplayMap`) producing `LookupOption[]` for one reference field. Reuses the same
 * cache `ObjectFormPage` / the detail page populate, so no extra fetch of the target collection.
 */
import { useMemo } from 'react'
import { useLookupDisplayMap } from '@/hooks/useLookupDisplayMap'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { LookupOption } from '@/components/LookupSelect'

export interface UseLookupOptionsResult {
  options: LookupOption[]
  isLoading: boolean
}

/**
 * Resolve the option list ({ id, label }[]) for a single reference field. When `enabled` is false
 * (editor mode) or no field is supplied, no fetch runs and the option list is empty.
 */
export function useLookupOptions(
  fieldDef: FieldDefinition | undefined,
  enabled = true
): UseLookupOptionsResult {
  // useLookupDisplayMap only fetches reference fields that carry a target — pass a single-field array.
  const fields = useMemo(() => (enabled && fieldDef ? [fieldDef] : []), [enabled, fieldDef])
  const { lookupDisplayMap, isLoading } = useLookupDisplayMap(fields)

  const options = useMemo<LookupOption[]>(() => {
    if (!fieldDef || !lookupDisplayMap) return []
    const idToLabel = lookupDisplayMap[fieldDef.name]
    if (!idToLabel) return []
    return Object.entries(idToLabel).map(([id, label]) => ({ id, label }))
  }, [fieldDef, lookupDisplayMap])

  return { options, isLoading }
}
