/**
 * Tenant Context
 *
 * Two modes of operation:
 * - slug: tenant identity comes from the URL path (/:tenantSlug/...). Used on
 *   the canonical platform host (app.kelta.io).
 * - custom-domain: the request hostname is bound to a tenant by the gateway,
 *   so the URL contains no slug (https://acme.com/...). The slug is fetched
 *   from /api/whoami once the API client is available.
 */

import React, { createContext, useContext, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'

export type TenantMode = 'slug' | 'custom-domain'

interface TenantContextValue {
  tenantSlug: string
  tenantBasePath: string
  mode: TenantMode
}

const TenantContext = createContext<TenantContextValue | null>(null)

// Module-level variable for non-hook access (set once per page load)
let _currentTenantSlug: string = 'default'
let _currentTenantId: string | null = null

/**
 * Returns true when the browser is on a tenant custom domain rather than the
 * canonical platform host (*.kelta.io).
 */
// eslint-disable-next-line react-refresh/only-export-components
export function isCustomDomainHost(): boolean {
  if (typeof window === 'undefined') return false
  const host = window.location.hostname.toLowerCase()
  if (!host) return false
  if (host === 'localhost' || host === '127.0.0.1') return false
  // Platform hosts: anything under kelta.io is slug-mode.
  return !(host === 'kelta.io' || host.endsWith('.kelta.io'))
}

/**
 * Returns the current tenant slug for use outside React components.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function getTenantSlug(): string {
  return _currentTenantSlug
}

function syncTenantSlug(slug: string): void {
  _currentTenantSlug = slug
}

// eslint-disable-next-line react-refresh/only-export-components
export function setResolvedTenantId(tenantId: string | null): void {
  _currentTenantId = tenantId
}

// eslint-disable-next-line react-refresh/only-export-components
export function getResolvedTenantId(): string | null {
  return _currentTenantId
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTenant(): TenantContextValue {
  const ctx = useContext(TenantContext)
  if (!ctx) {
    throw new Error('useTenant must be used within a TenantProvider')
  }
  return ctx
}

export function TenantProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const customDomain = isCustomDomainHost()
  const mode: TenantMode = customDomain ? 'custom-domain' : 'slug'

  // In custom-domain mode the slug isn't in the URL — we hydrate it later
  // from /api/whoami. Until that completes we have no slug to show, which is
  // fine: routing and API calls work without it on a custom domain.
  const [resolvedSlug, setResolvedSlug] = useState<string>(() =>
    customDomain ? '' : (tenantSlug || 'default'),
  )

  syncTenantSlug(resolvedSlug)

  useEffect(() => {
    if (customDomain) {
      // Worker echoes the gateway-injected X-Tenant-Slug header back so the UI
      // can label things by tenant. No auth needed (custom-domain mode resolves
      // tenant from Host pre-auth) — but the endpoint is also fine post-auth.
      fetch('/api/whoami', { credentials: 'include' })
        .then((r) => (r.ok ? r.json() : null))
        .then((j) => {
          if (j && typeof j.tenantSlug === 'string' && j.tenantSlug) {
            setResolvedSlug(j.tenantSlug)
            syncTenantSlug(j.tenantSlug)
          }
        })
        .catch(() => {
          // Silent: tenant labels degrade gracefully without a slug
        })
      return
    }
    const next = tenantSlug || 'default'
    setResolvedSlug(next)
    syncTenantSlug(next)
  }, [customDomain, tenantSlug])

  const value: TenantContextValue = {
    tenantSlug: resolvedSlug,
    tenantBasePath: customDomain ? '' : `/${resolvedSlug}`,
    mode,
  }

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>
}
