/**
 * useTenantSettings
 *
 * Read + mutate the current tenant's `settings` JSONB column via the
 * existing tenants JSON:API surface. Cached per-tenant via react-query so
 * downstream consumers (AddressMap, integrations panels) don't refetch.
 */

import { useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import { useTenant } from '@/context/TenantContext'

export interface TenantSettings {
  /**
   * Map / location settings — used by AddressMap. When `token` is empty,
   * the static-map renderer falls back to the free OSM staticmap service.
   */
  map?: {
    /** Mapbox public access token */
    token?: string
    /** Mapbox style id (e.g. `mapbox/dark-v11`) or maplibre style URL */
    style?: string
  }
  /** Free-form bag — other tenant settings live alongside */
  [key: string]: unknown
}

interface TenantResource {
  id: string
  slug: string
  name: string
  settings: TenantSettings | null
}

const QUERY_KEY = (slug: string): unknown[] => ['tenant-settings', slug]

export function useTenantSettings(): {
  tenant: TenantResource | null
  settings: TenantSettings
  isLoading: boolean
  error: Error | null
  save: (next: TenantSettings) => Promise<void>
  isSaving: boolean
} {
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()
  const queryClient = useQueryClient()

  const { data, isLoading, error } = useQuery<TenantResource | null, Error>({
    queryKey: QUERY_KEY(tenantSlug),
    queryFn: async () => {
      const list = await apiClient.getList<TenantResource>(
        `/api/tenants?filter[slug][eq]=${encodeURIComponent(tenantSlug)}&page[size]=1`
      )
      return list[0] ?? null
    },
    staleTime: 60 * 1000,
  })

  const saveMutation = useMutation({
    mutationFn: async (next: TenantSettings) => {
      if (!data) throw new Error('Tenant not loaded yet')
      await apiClient.putResource(`/api/tenants/${data.id}`, {
        slug: data.slug,
        name: data.name,
        settings: next,
      })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY(tenantSlug) })
    },
  })

  const settings = useMemo<TenantSettings>(() => data?.settings ?? {}, [data?.settings])

  return {
    tenant: data ?? null,
    settings,
    isLoading,
    error: error ?? null,
    save: async (next) => {
      await saveMutation.mutateAsync(next)
    },
    isSaving: saveMutation.isPending,
  }
}
