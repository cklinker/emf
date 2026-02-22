/**
 * useCollectionSchema Hook
 *
 * Fetches and caches a collection's schema (name, displayName, fields)
 * from the control plane. Field types are normalized from backend
 * canonical form (uppercase) to UI form (lowercase).
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import type { ApiClient } from '../services/apiClient'

/**
 * Field type union covering all supported field types.
 */
export type FieldType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'date'
  | 'datetime'
  | 'json'
  | 'reference'
  | 'picklist'
  | 'multi_picklist'
  | 'currency'
  | 'percent'
  | 'auto_number'
  | 'phone'
  | 'email'
  | 'url'
  | 'rich_text'
  | 'encrypted'
  | 'external_id'
  | 'geolocation'
  | 'lookup'
  | 'master_detail'
  | 'formula'
  | 'rollup_summary'

/**
 * Field definition from the collection schema.
 */
export interface FieldDefinition {
  id: string
  name: string
  displayName?: string
  type: FieldType
  required: boolean
  referenceTarget?: string
  referenceCollectionId?: string
  /** Picklist enum values (populated at runtime by form pages) */
  enumValues?: string[]
  /** Lookup options for reference fields (populated at runtime by form pages) */
  lookupOptions?: Array<{ id: string; label: string }>
}

/**
 * Collection schema with fields.
 */
export interface CollectionSchema {
  id: string
  name: string
  displayName: string
  displayFieldName?: string
  fields: FieldDefinition[]
}

/**
 * Reverse mapping from backend canonical types (uppercase) to UI types (lowercase).
 */
const BACKEND_TYPE_TO_UI: Record<string, FieldType> = {
  DOUBLE: 'number',
  INTEGER: 'number',
  LONG: 'number',
  JSON: 'json',
  ARRAY: 'json',
  REFERENCE: 'master_detail',
  LOOKUP: 'master_detail',
}

function normalizeFieldType(backendType: string): FieldType {
  const upper = backendType.toUpperCase()
  if (upper in BACKEND_TYPE_TO_UI) {
    return BACKEND_TYPE_TO_UI[upper]
  }
  return backendType.toLowerCase() as FieldType
}

/**
 * Fetch a collection schema by name from the control plane.
 * The backend resolves by name or ID via getCollectionByIdOrName(),
 * so a single call to /control/collections/{name} suffices.
 */
async function fetchCollectionSchema(
  apiClient: ApiClient,
  collectionName: string
): Promise<CollectionSchema> {
  // Backend resolves by name or ID — no need to fetch all collections first
  const schema = await apiClient.get<CollectionSchema>(
    `/control/collections/${encodeURIComponent(collectionName)}`
  )

  // Normalize field types from backend canonical form to UI form
  if (schema.fields) {
    schema.fields = schema.fields.map((f) => ({
      ...f,
      type: normalizeFieldType(f.type),
    }))
  }

  return schema
}

export interface UseCollectionSchemaReturn {
  schema: CollectionSchema | undefined
  fields: FieldDefinition[]
  isLoading: boolean
  error: Error | null
}

/**
 * Hook to fetch and cache a collection schema.
 *
 * @param collectionName - The collection name to fetch the schema for
 * @returns Collection schema, fields array, loading/error states
 */
export function useCollectionSchema(collectionName: string | undefined): UseCollectionSchemaReturn {
  const { apiClient } = useApi()

  const {
    data: schema,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['collection-schema', collectionName],
    queryFn: () => fetchCollectionSchema(apiClient, collectionName!),
    enabled: !!collectionName,
    staleTime: 5 * 60 * 1000, // 5 minutes — metadata changes rarely
  })

  return {
    schema,
    fields: schema?.fields ?? [],
    isLoading,
    error: error as Error | null,
  }
}
