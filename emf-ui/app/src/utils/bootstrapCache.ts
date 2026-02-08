/**
 * Shared bootstrap config cache.
 *
 * Both AuthContext and ConfigContext need data from /control/ui-bootstrap.
 * This module ensures only one HTTP request is made by caching the in-flight
 * promise so concurrent callers share the same fetch.
 */

const BOOTSTRAP_URL = `${import.meta.env.VITE_API_BASE_URL || ''}/control/ui-bootstrap`

let cachedPromise: Promise<unknown> | null = null

/**
 * Fetch the bootstrap config, deduplicating concurrent requests.
 * Returns the raw parsed JSON. Callers are responsible for validation.
 */
export function fetchBootstrapConfig(): Promise<unknown> {
  if (cachedPromise) {
    return cachedPromise
  }

  cachedPromise = fetch(BOOTSTRAP_URL)
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
}
