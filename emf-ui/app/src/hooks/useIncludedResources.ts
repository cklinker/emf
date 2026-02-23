/**
 * useIncludedResources Hook
 *
 * Extracts related resources from a JSON:API response's `included` array.
 * When using `?include=profile,manager`, the response contains an `included`
 * array with all sideloaded resources. This hook extracts and organizes them
 * into display-ready maps.
 *
 * This replaces the pattern of making separate API calls per reference field
 * (e.g., `useLookupDisplayMap`) with a single request using JSON:API includes.
 */

import { useMemo } from 'react'
import { extractIncluded, buildIncludedDisplayMap } from '../utils/jsonapi'

export interface IncludedResourceConfig {
  /** JSON:API resource type to extract (e.g. "profiles", "users") */
  type: string
  /** Field(s) to use for display. String for single field, array for composite. */
  displayField: string | string[]
}

export interface UseIncludedResourcesReturn {
  /**
   * Map of resource type → { id → display value }.
   * Example: { profiles: { "p1": "Admin", "p2": "User" }, users: { "u1": "Alice" } }
   */
  displayMaps: Record<string, Record<string, string>>
  /**
   * Convenience: get the display value for a reference field.
   * Example: getDisplayValue("profiles", profileId) → "Admin"
   */
  getDisplayValue: (type: string, id: string | undefined | null) => string
}

/**
 * Extract display maps from JSON:API included resources.
 *
 * Replaces `useLookupDisplayMap` — instead of N+1 API calls, use a single
 * request with `?include=` and extract display values from the response.
 *
 * ```tsx
 * const { data: users, ...rest } = useResources({
 *   resource: 'users',
 *   include: ['profile', 'manager']
 * })
 *
 * const { getDisplayValue } = useIncludedResources(rest.rawResponse, [
 *   { type: 'profiles', displayField: 'name' },
 *   { type: 'users', displayField: ['firstName', 'lastName'] }
 * ])
 *
 * // In table renderer:
 * getDisplayValue('profiles', user.profileId)  // → "Admin"
 * getDisplayValue('users', user.managerId)     // → "Alice Smith"
 * ```
 */
export function useIncludedResources(
  response: unknown,
  configs: IncludedResourceConfig[]
): UseIncludedResourcesReturn {
  const displayMaps = useMemo(() => {
    const maps: Record<string, Record<string, string>> = {}

    for (const config of configs) {
      maps[config.type] = buildIncludedDisplayMap(response, config.type, config.displayField)
    }

    return maps
  }, [response, configs])

  const getDisplayValue = useMemo(() => {
    return (type: string, id: string | undefined | null): string => {
      if (!id) return ''
      return displayMaps[type]?.[id] ?? id
    }
  }, [displayMaps])

  return { displayMaps, getDisplayValue }
}

/**
 * Extract all included resources of a given type as flat records.
 *
 * Useful when you need the full record data, not just a display value.
 *
 * ```tsx
 * const profiles = useExtractIncluded<ProfileRecord>(response, 'profiles')
 * // profiles = [{ id: "p1", name: "Admin", ... }, { id: "p2", name: "User", ... }]
 * ```
 */
export function useExtractIncluded<T extends Record<string, unknown> = Record<string, unknown>>(
  response: unknown,
  type: string
): T[] {
  return useMemo(() => extractIncluded<T>(response, type), [response, type])
}
