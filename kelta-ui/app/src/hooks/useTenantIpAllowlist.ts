/**
 * useTenantIpAllowlist
 *
 * Read + mutate the current tenant's IP allowlist configuration
 * (`ipAllowlistEnabled` + `ipAllowlistCidrs`) via the tenants JSON:API surface.
 * These are first-class attributes on the tenant resource (not the free-form
 * `settings` bag), so writes flow through the tenant BeforeSaveHook, which
 * validates the CIDRs and broadcasts the change to every gateway pod.
 */

import { useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import { useTenant } from '@/context/TenantContext'

interface TenantIpResource {
  id: string
  slug: string
  name: string
  ipAllowlistEnabled?: boolean | null
  ipAllowlistCidrs?: string[] | null
}

export interface TenantIpAllowlist {
  enabled: boolean
  cidrs: string[]
}

const QUERY_KEY = (slug: string): unknown[] => ['tenant-ip-allowlist', slug]

export function useTenantIpAllowlist(): {
  allowlist: TenantIpAllowlist
  isLoading: boolean
  error: Error | null
  save: (next: TenantIpAllowlist) => Promise<void>
  isSaving: boolean
} {
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()
  const queryClient = useQueryClient()

  const { data, isLoading, error } = useQuery<TenantIpResource | null, Error>({
    queryKey: QUERY_KEY(tenantSlug),
    queryFn: async () => {
      const list = await apiClient.getList<TenantIpResource>(
        `/api/tenants?filter[slug][eq]=${encodeURIComponent(tenantSlug)}&page[size]=1`
      )
      return list[0] ?? null
    },
    staleTime: 60 * 1000,
  })

  const saveMutation = useMutation({
    mutationFn: async (next: TenantIpAllowlist) => {
      if (!data) throw new Error('Tenant not loaded yet')
      await apiClient.putResource(`/api/tenants/${data.id}`, {
        slug: data.slug,
        name: data.name,
        ipAllowlistEnabled: next.enabled,
        ipAllowlistCidrs: next.cidrs,
      })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY(tenantSlug) })
    },
  })

  const allowlist = useMemo<TenantIpAllowlist>(
    () => ({
      enabled: data?.ipAllowlistEnabled ?? false,
      cidrs: data?.ipAllowlistCidrs ?? [],
    }),
    [data?.ipAllowlistEnabled, data?.ipAllowlistCidrs]
  )

  return {
    allowlist,
    isLoading,
    error: error ?? null,
    save: async (next) => {
      await saveMutation.mutateAsync(next)
    },
    isSaving: saveMutation.isPending,
  }
}
