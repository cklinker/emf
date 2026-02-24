/**
 * Shared bootstrap config cache.
 *
 * Both AuthContext and ConfigContext need data from the bootstrap
 * configuration. This module composes the bootstrap response from
 * parallel JSON:API calls to /api/ui-pages, /api/ui-menus, and
 * /api/oidc-providers (served by the DynamicCollectionRouter).
 *
 * The URLs are tenant-scoped: /{tenantSlug}/api/{collection}.
 *
 * A single in-flight promise is cached so concurrent callers
 * (AuthContext + ConfigContext) share the same set of fetches.
 */

import { getTenantSlug } from '../context/TenantContext'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

let cachedPromise: Promise<unknown> | null = null
let cachedSlug: string | null = null

/**
 * Default theme when no theme collection exists yet.
 */
const DEFAULT_THEME = {
  primaryColor: '#1976d2',
  secondaryColor: '#dc004e',
  fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  borderRadius: '4px',
}

/**
 * Default branding when no branding collection exists yet.
 */
const DEFAULT_BRANDING = {
  logoUrl: '/logo.svg',
  applicationName: 'EMF Platform',
  faviconUrl: '/favicon.ico',
}

/**
 * Unwrap a JSON:API list response into a flat array of objects.
 * Each resource object's id + attributes are merged into a plain object.
 */
function unwrapList(body: unknown): Record<string, unknown>[] {
  if (!body || typeof body !== 'object') return []
  const obj = body as Record<string, unknown>
  if (Array.isArray(obj.data)) {
    return (obj.data as Array<Record<string, unknown>>).map((item) => {
      const attrs = (item.attributes || {}) as Record<string, unknown>
      return { id: item.id, ...attrs }
    })
  }
  if (Array.isArray(body)) return body as Record<string, unknown>[]
  return []
}

/**
 * Fetch the bootstrap config by composing parallel JSON:API calls.
 * Returns a shape compatible with the BootstrapConfig interface.
 * Callers are responsible for validation.
 *
 * The URLs are scoped to the current tenant slug from the URL path.
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

  const base = `${BASE_URL}/${slug}`

  cachedPromise = Promise.all([
    fetch(`${base}/api/ui-pages?page[size]=500`).then(async (r) => {
      if (!r.ok) return { data: [] }
      return r.json()
    }),
    fetch(`${base}/api/ui-menus?page[size]=500`).then(async (r) => {
      if (!r.ok) return { data: [] }
      return r.json()
    }),
    fetch(`${base}/api/oidc-providers?page[size]=100`).then(async (r) => {
      if (!r.ok) return { data: [] }
      return r.json()
    }),
    // Fetch tenant info to get tenantId
    fetch(`${base}/api/tenants?filter[slug][eq]=${encodeURIComponent(slug)}&page[size]=1`).then(
      async (r) => {
        if (!r.ok) return { data: [] }
        return r.json()
      }
    ),
  ])
    .then(([pagesRes, menusRes, providersRes, tenantsRes]) => {
      const pages = unwrapList(pagesRes)
      const menus = unwrapList(menusRes)
      const oidcProviders = unwrapList(providersRes)
      const tenants = unwrapList(tenantsRes)
      const tenantId = tenants.length > 0 ? (tenants[0].id as string) : undefined

      return {
        pages,
        menus,
        oidcProviders,
        theme: DEFAULT_THEME,
        branding: DEFAULT_BRANDING,
        tenantId,
      }
    })
    .catch((err) => {
      // Clear cache on error so next call retries
      cachedPromise = null
      throw err instanceof Error
        ? err
        : new Error('Failed to fetch bootstrap configuration: Unknown error')
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
