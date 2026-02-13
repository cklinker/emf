/**
 * Shared bootstrap config cache.
 *
 * Both AuthContext and ConfigContext need data from /control/ui-bootstrap.
 * This module ensures only one HTTP request is made per tenant slug by caching
 * the in-flight promise so concurrent callers share the same fetch.
 *
 * The bootstrap URL is tenant-scoped: /{tenantSlug}/control/ui-bootstrap.
 */

import { getTenantSlug } from '../context/TenantContext'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

let cachedPromise: Promise<unknown> | null = null
let cachedSlug: string | null = null

/**
 * Fetch the bootstrap config, deduplicating concurrent requests.
 * Returns the raw parsed JSON. Callers are responsible for validation.
 *
 * The URL is scoped to the current tenant slug from the URL path.
 */
export function fetchBootstrapConfig(): Promise<unknown> {
  const slug = getTenantSlug()

  // If slug changed, invalidate cache
  if (cachedSlug !== slug) {
    cachedPromise = null
    cachedSlug = slug
  }

  if (cachedPromise) {
    return cachedPromise
  }

  const url = `${BASE_URL}/${slug}/control/ui-bootstrap`

  cachedPromise = fetch(url)
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(
          `Failed to fetch bootstrap configuration: ${response.status} ${response.statusText}`
        )
      }
      try {
        return await response.json()
      } catch {
        throw new Error('Failed to parse bootstrap configuration: Invalid JSON response')
      }
    })
    .catch((err) => {
      // Clear cache on error so next call retries
      cachedPromise = null
      throw err
    })

  return cachedPromise
}

/**
 * Clear the cached bootstrap response.
 * Call this when you need to force a fresh fetch (e.g. config reload).
 */
export function clearBootstrapCache(): void {
  cachedPromise = null
  cachedSlug = null
}
