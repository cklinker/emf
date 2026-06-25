/**
 * `useFieldDef` (slice 2f) — the single place that maps a `{ collection, field }` pair to its
 * `FieldDefinition` (type + config). Wraps `useCollectionSchema` and finds the field by `name`.
 */
import { useMemo } from 'react'
import { useCollectionSchema, type FieldDefinition } from '@/hooks/useCollectionSchema'

export interface UseFieldDefResult {
  fieldDef: FieldDefinition | undefined
  isLoading: boolean
}

export function useFieldDef(
  collection: string | undefined,
  field: string | undefined
): UseFieldDefResult {
  const { fields, isLoading } = useCollectionSchema(collection)
  const fieldDef = useMemo(
    () => (field ? fields.find((f) => f.name === field) : undefined),
    [fields, field]
  )
  return { fieldDef, isLoading }
}
