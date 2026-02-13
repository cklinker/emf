/**
 * Hook to provide the current tenant ID.
 *
 * Reads tenant identity from TenantContext (URL slug) and the resolved
 * tenant ID from the bootstrap config. Centralizes tenant ID resolution
 * so it can be changed in one place instead of being hardcoded across
 * dozens of API calls.
 */

import { getTenantSlug, getResolvedTenantId } from '../context/TenantContext'

/**
 * Returns the current tenant slug for API calls.
 * This is a React hook — call it from within a component.
 * The slug comes from the URL path (/:tenantSlug/...).
 */
export function useTenantId(): string {
  return getTenantSlug()
}

/**
 * Returns the tenant slug for use outside of React components (e.g., in utility functions).
 */
export function getTenantId(): string {
  return getResolvedTenantId() || getTenantSlug()
}
