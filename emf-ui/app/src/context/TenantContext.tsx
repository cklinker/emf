/**
 * Tenant Context
 *
 * Provides tenant identity from the URL path slug (/:tenantSlug/...).
 * Sets a module-level variable so getTenantSlug() works outside React components.
 */

import React, { createContext, useContext, useEffect } from 'react'
import { useParams } from 'react-router-dom'

interface TenantContextValue {
  tenantSlug: string
  tenantBasePath: string
}

const TenantContext = createContext<TenantContextValue | null>(null)

// Module-level variable for non-hook access (set once per page load)
let _currentTenantSlug: string = 'default'
let _currentTenantId: string | null = null

/**
 * Returns the current tenant slug for use outside React components.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function getTenantSlug(): string {
  return _currentTenantSlug
}

/**
 * Sets the resolved tenant ID from bootstrap config.
 * Called by AuthContext/ConfigContext after fetching bootstrap.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function setResolvedTenantId(tenantId: string | null): void {
  _currentTenantId = tenantId
}

/**
 * Returns the resolved tenant ID (from bootstrap) for use outside React components.
 */
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
  const slug = tenantSlug || 'default'

  // Keep module-level variable in sync
  useEffect(() => {
    _currentTenantSlug = slug
  }, [slug])

  const value: TenantContextValue = {
    tenantSlug: slug,
    tenantBasePath: `/${slug}`,
  }

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>
}
