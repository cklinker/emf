/**
 * CustomPage
 *
 * Renders a published custom page built in the page builder. Resolves the slug from the URL,
 * fetches the versioned render contract from `GET /api/pages/{slug}/render`, and renders its
 * component tree via {@link PageTreeRenderer}. Unpublished / unknown slugs resolve to a
 * "Page not found" state (the endpoint returns 404).
 *
 * Route: /:tenantSlug/app/p/:pageSlug
 */

import React from 'react'
import { useParams, Link } from 'react-router-dom'
import { Loader2, FileQuestion, WifiOff } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Card, CardContent } from '@/components/ui/card'
import { useApi } from '@/context/ApiContext'
import { useOffline } from '@/offline'
import { PageTreeRenderer, type PageNode } from './PageTreeRenderer'
import type { PageDataSource, PageVariable } from '@/pages/PageBuilderPage/pageConfig'
import { usePageVariables } from '@/pages/PageBuilderPage/hooks/usePageVariables'
import { usePageDataSources } from '@/pages/PageBuilderPage/hooks/usePageDataSources'
import { evaluateComputedVariables } from '@/pages/PageBuilderPage/model/computedVars'
import type { BindingScope } from '@/pages/PageBuilderPage/model/bindingScope'
import type { PageRuntimeValue } from '@/pages/PageBuilderPage/runtime/PageRuntimeContext'

/**
 * Versioned page render contract returned by GET /api/pages/{slug}/render.
 *
 * `variables` / `dataSources` are page-level config surfaced verbatim (the server never resolves
 * bindings or fetches data sources — that stays on the authorized JSON:API path). They are consumed
 * by slices 2d/2e; here they are typed only so the contract is stable and they are not dropped.
 */
interface PageRenderContract {
  version: string
  slug: string
  title: string | null
  path: string | null
  variables: PageVariable[]
  dataSources: PageDataSource[]
  tree: { components?: PageNode[]; layout?: unknown }
}

export interface CustomPageProps {
  /**
   * When set, renders this page slug instead of the `:pageSlug` route param. Used by `AppHomePage` to
   * render a `config.isHomePage` page in place of the default home (the URL stays `/app/home`).
   */
  slug?: string
}

export function CustomPage({ slug: slugOverride }: CustomPageProps = {}): React.ReactElement {
  const { tenantSlug, pageSlug: routePageSlug } = useParams<{
    tenantSlug: string
    pageSlug: string
  }>()
  const pageSlug = slugOverride ?? routePageSlug
  const basePath = `/${tenantSlug}/app`
  const { apiClient } = useApi()

  // Offline replica context (present under EndUserShell; undefined on admin previews, which
  // then keep the online-only behavior). Online fetches write the contract through to the
  // replica so the page shell still renders cold-offline (data sources already do the same).
  const offline = useOffline()
  const isOnline = offline?.online !== false

  const { data: contract, isLoading } = useQuery({
    // `isOnline` in the key so a reconnect re-runs the online fetch path.
    queryKey: ['page-render', pageSlug, isOnline],
    queryFn: async (): Promise<PageRenderContract | null> => {
      const slug = pageSlug!
      const readCache = async (): Promise<PageRenderContract | null> => {
        if (!offline) return null
        const cached = (await offline.store.getPageContract(slug)) as PageRenderContract | undefined
        return cached ?? null
      }

      // Offline: serve the cached contract from the local replica.
      if (offline && !isOnline) return readCache()

      try {
        const fetched = await apiClient.get<PageRenderContract>(
          `/api/pages/${encodeURIComponent(slug)}/render`
        )
        if (offline) await offline.store.putPageContract(slug, fetched)
        return fetched
      } catch {
        // 404 (unpublished/unknown) or transient error → fall back to the replica before
        // rendering the not-found state.
        return readCache()
      }
    },
    enabled: !!pageSlug,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  // Build the live binding scope CLIENT-SIDE (slice 2d). Hooks run unconditionally (before any early
  // return) with empty defaults when there is no contract yet. The server resolves NOTHING — vars are
  // seeded from the contract's declared defaults and each data source is fetched over the authorized
  // JSON:API path so Cerbos/FLS stay enforced server-side.
  const variables = React.useMemo<PageVariable[]>(() => contract?.variables ?? [], [contract])
  const dataSources = React.useMemo<PageDataSource[]>(() => contract?.dataSources ?? [], [contract])
  const { vars, setVar } = usePageVariables(variables)
  const page = React.useMemo(
    () => ({ slug: pageSlug, params: pageSlug ? { pageSlug } : undefined }),
    [pageSlug]
  )
  // Data sources may read vars/page in their filter/recordId bindings. They see STATIC
  // vars only — a computed var whose inputs are data would be a fetch↔derive cycle.
  const { data } = usePageDataSources(dataSources, { vars, page }, pageSlug)
  // Computed variables (app-platform slice 2): derive over the live scope and merge
  // into `vars` so bindings/visibility/actions read them like any variable.
  const scope: BindingScope = React.useMemo(() => {
    const computed = evaluateComputedVariables(variables, { vars, data, page })
    return { vars: { ...vars, ...computed }, data, page }
  }, [variables, vars, data, page])

  // Page-level action deps (slice 2e): `setVar` writes a page variable; `dataSourceQueryKey` mirrors the
  // `usePageDataSources` query-key prefix so `refreshData` invalidates the right source's on-load fetch.
  const runtime = React.useMemo<PageRuntimeValue>(
    () => ({
      tenantSlug: tenantSlug || '',
      setVar,
      dataSourceQueryKey: (name: string) => ['page-data', pageSlug ?? '', name],
    }),
    [tenantSlug, setVar, pageSlug]
  )

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!contract) {
    // Cold-offline with no cached contract is a connectivity gap, not a missing page —
    // say so instead of the misleading "Page not found".
    const unavailableOffline = !!offline && !isOnline
    return (
      <div className="space-y-4 p-6">
        <Breadcrumb>
          <BreadcrumbList>
            <BreadcrumbItem>
              <BreadcrumbLink asChild>
                <Link to={`${basePath}/home`}>Home</Link>
              </BreadcrumbLink>
            </BreadcrumbItem>
            <BreadcrumbSeparator />
            <BreadcrumbItem>
              <BreadcrumbPage>{pageSlug}</BreadcrumbPage>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
        <Card>
          <CardContent className="flex flex-col items-center gap-4 py-12 text-center">
            {unavailableOffline ? (
              <>
                <WifiOff className="h-12 w-12 text-muted-foreground" />
                <div className="space-y-2">
                  <h2 className="text-lg font-semibold text-foreground">
                    This page isn&apos;t available offline
                  </h2>
                  <p className="text-sm text-muted-foreground">
                    The page &quot;{pageSlug}&quot; hasn&apos;t been visited while online yet.
                    Reconnect to load it.
                  </p>
                </div>
              </>
            ) : (
              <>
                <FileQuestion className="h-12 w-12 text-muted-foreground" />
                <div className="space-y-2">
                  <h2 className="text-lg font-semibold text-foreground">Page not found</h2>
                  <p className="text-sm text-muted-foreground">
                    The page &quot;{pageSlug}&quot; is not published or does not exist.
                  </p>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    )
  }

  const pageTitle = contract.title || pageSlug || 'Custom Page'

  return (
    <div className="space-y-4 p-6" data-testid="custom-page">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to={`${basePath}/home`}>Home</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{pageTitle}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <PageTreeRenderer
        components={contract.tree?.components ?? []}
        tenantSlug={tenantSlug || ''}
        scope={scope}
        runtime={runtime}
      />
    </div>
  )
}
