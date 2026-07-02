/**
 * On-load data-source fetch (slice 2d, parent §"On-load fetch contract"). For each {@link PageDataSource}
 * the hook resolves its dynamic config (`filter` / `recordId` bindings) against the CURRENT scope on the
 * client, then fetches through `apiClient` — the SAME authorized JSON:API path `DataTableNode` uses, so
 * the gateway + worker enforce Cerbos route authz + FLS. The server resolves nothing; every record read
 * is a normal GET. Results are exposed at `scope.data[<name>]`.
 *
 * Caps (parent §"DoS / fan-out caps"): at most {@link MAX_PAGE_DATA_SOURCES} sources fire — extras are
 * ignored (defensive slice + a warn), bounding fan-out even for a hand-edited config. List page size is
 * clamped to the server's {@link MAX_HTTP_PAGE_SIZE}.
 */
import { useMemo } from 'react'
import { useQueries, useQueryClient } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import { useOffline } from '@/offline'
import type { OfflineContextValue, ReplicaRecord } from '@/offline'
import type { ApiClient } from '@/services/apiClient'
import type { PageDataSource } from '../pageConfig'
import { MAX_PAGE_DATA_SOURCES } from '../model/limits'
import { resolveBinding } from '../model/resolveBindings'
import { isBinding } from '../model/pageModel'
import type { Binding, PropValue } from '../model/pageModel'
import type { BindingScope } from '../model/bindingScope'

/** Defensive clamp matching the server's `MAX_HTTP_PAGE_SIZE`. */
const MAX_HTTP_PAGE_SIZE = 200

export interface UsePageDataSourcesReturn {
  /** Per-source results keyed by source name — feeds `scope.data`. */
  data: Record<string, unknown>
  /** Re-run one source's query (wired to 2e's `refreshData`). */
  refresh: (name: string) => void
  isLoading: boolean
}

/** Resolve a possibly-bound config value against the scope (a literal passes through). */
function resolveValue(value: PropValue | undefined, scope: BindingScope): unknown {
  if (value === undefined) return undefined
  return isBinding(value) ? resolveBinding(value as Binding, scope) : value
}

/** Compose the authorized JSON:API list URL for a source, resolving bound filter values client-side. */
export function buildListUrl(src: PageDataSource, scope: BindingScope): string {
  const params: string[] = []
  const size = Math.min(src.limit ?? 25, MAX_HTTP_PAGE_SIZE)
  params.push(`page[size]=${size}`)
  if (src.fields && src.fields.length > 0) {
    params.push(`fields[${src.collection}]=${encodeURIComponent(src.fields.join(','))}`)
  }
  if (src.filter && typeof src.filter === 'object') {
    for (const [k, raw] of Object.entries(src.filter as Record<string, unknown>)) {
      const resolved = resolveValue(raw as PropValue, scope)
      if (resolved != null && resolved !== '') {
        params.push(`filter[${k}][EQ]=${encodeURIComponent(String(resolved))}`)
      }
    }
  }
  if (src.sort && src.sort.length > 0) {
    params.push(`sort=${encodeURIComponent(src.sort.join(','))}`)
  }
  return `/api/${src.collection}?${params.join('&')}`
}

/** A stable hash of a source's resolved dynamic config — drives the React Query cache key. */
function resolvedConfigKey(src: PageDataSource, scope: BindingScope): string {
  if (src.mode === 'single') {
    return String(resolveValue(src.recordId as PropValue, scope) ?? '')
  }
  return buildListUrl(src, scope)
}

async function fetchSource(
  apiClient: ApiClient,
  src: PageDataSource,
  scope: BindingScope,
  offline?: OfflineContextValue
): Promise<unknown> {
  const isOnline = offline?.online !== false

  if (src.mode === 'single') {
    const id = resolveValue(src.recordId as PropValue, scope)
    if (id == null || id === '') return null
    const key = String(id)
    // Offline: serve the record from the local replica.
    if (offline && !isOnline) {
      return (await offline.store.get(src.collection, key)) ?? null
    }
    try {
      const record = await apiClient.getOne<Record<string, unknown>>(
        `/api/${src.collection}/${encodeURIComponent(key)}`
      )
      if (offline && record && typeof record === 'object' && 'id' in record) {
        offline.registerCollection(src.collection)
        await offline.store.putRecords(src.collection, [record as ReplicaRecord])
      }
      return record
    } catch {
      // 404 / denied → null (Cerbos/FLS apply server-side; a missing record reads as absent).
      return null
    }
  }

  // Offline: serve cached rows for the collection.
  if (offline && !isOnline) {
    return offline.store.getAll(src.collection)
  }
  const rows = await apiClient.getList<Record<string, unknown>>(buildListUrl(src, scope))
  if (offline) {
    offline.registerCollection(src.collection)
    await offline.store.putRecords(src.collection, rows as ReplicaRecord[])
  }
  return rows
}

export function usePageDataSources(
  dataSources: PageDataSource[],
  scope: BindingScope,
  pageSlug?: string
): UsePageDataSourcesReturn {
  const { apiClient } = useApi()
  const offline = useOffline()
  const queryClient = useQueryClient()
  const isOnline = offline?.online !== false

  // Defensive cap: only the first MAX_PAGE_DATA_SOURCES sources fire (a hand-edited config can't fan out).
  const sources = useMemo(() => {
    if (dataSources.length > MAX_PAGE_DATA_SOURCES) {
      console.warn(
        `[usePageDataSources] ${dataSources.length} sources declared; capping at ${MAX_PAGE_DATA_SOURCES}.`
      )
      return dataSources.slice(0, MAX_PAGE_DATA_SOURCES)
    }
    return dataSources
  }, [dataSources])

  const queryKeyFor = (src: PageDataSource): unknown[] => [
    'page-data',
    pageSlug ?? '',
    src.name,
    resolvedConfigKey(src, scope),
    // `isOnline` in the key so a reconnect re-runs the online fetch path.
    isOnline,
  ]

  const results = useQueries({
    queries: sources.map((src) => ({
      queryKey: queryKeyFor(src),
      queryFn: () => fetchSource(apiClient, src, scope, offline),
      enabled: !!src.collection,
      staleTime: 60 * 1000,
      retry: false,
    })),
  })

  const data = useMemo(() => {
    const out: Record<string, unknown> = {}
    sources.forEach((src, i) => {
      out[src.name] = results[i]?.data ?? (src.mode === 'single' ? null : [])
    })
    return out
  }, [sources, results])

  const isLoading = results.some((r) => r.isLoading)

  const refresh = (name: string): void => {
    const src = sources.find((s) => s.name === name)
    if (!src) return
    queryClient.invalidateQueries({ queryKey: ['page-data', pageSlug ?? '', src.name] })
  }

  return { data, refresh, isLoading }
}
