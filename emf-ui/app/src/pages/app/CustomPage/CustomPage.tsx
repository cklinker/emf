/**
 * CustomPage
 *
 * Renders a custom page defined in the control plane's page configuration.
 * Resolves the page slug from the URL, fetches the page definition,
 * and renders the appropriate component from the ComponentRegistry.
 *
 * If the component is not found in the registry, shows a fallback
 * message directing the user to register the component via a plugin.
 *
 * Route: /:tenantSlug/app/p/:pageSlug
 */

import React from 'react'
import { useParams, Link } from 'react-router-dom'
import { Loader2, AlertCircle, FileQuestion } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent } from '@/components/ui/card'
import { useApi } from '@/context/ApiContext'
import { componentRegistry } from '@/services/componentRegistry'
import type { ApiClient } from '@/services/apiClient'

/**
 * Page definition returned from the control plane.
 */
interface CustomPageDefinition {
  id: string
  path: string
  title: string
  /** Component name to resolve from the ComponentRegistry */
  component: string
  /** Props to pass to the component */
  props?: Record<string, unknown>
  /** Optional: associated collection name */
  collectionName?: string
}

/**
 * Fetch a custom page definition by slug.
 */
async function fetchPageDefinition(
  apiClient: ApiClient,
  pageSlug: string
): Promise<CustomPageDefinition | null> {
  try {
    const response = await apiClient.get<CustomPageDefinition>(
      `/control/ui/pages/${encodeURIComponent(pageSlug)}`
    )
    return response || null
  } catch {
    // Endpoint not yet implemented — return null
    return null
  }
}

/**
 * Renders a component resolved from the ComponentRegistry.
 * Uses React.createElement to avoid the static-components lint rule
 * that flags dynamically-resolved components used in JSX.
 */
function ResolvedPageComponent({
  componentName,
  config,
  tenantSlug,
  collectionName,
}: {
  componentName: string
  config?: Record<string, unknown>
  tenantSlug: string
  collectionName?: string
}): React.ReactElement | null {
  const Comp = componentRegistry.getPageComponent(componentName)
  if (!Comp) return null
  return React.createElement(Comp, { config, tenantSlug, collectionName })
}

export function CustomPage(): React.ReactElement {
  const { tenantSlug, pageSlug } = useParams<{
    tenantSlug: string
    pageSlug: string
  }>()
  const basePath = `/${tenantSlug}/app`
  const { apiClient } = useApi()

  const {
    data: pageDefinition,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['custom-page', pageSlug],
    queryFn: () => fetchPageDefinition(apiClient, pageSlug!),
    enabled: !!pageSlug,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  // Check if the component is registered
  const hasComponent = pageDefinition?.component
    ? componentRegistry.hasPageComponent(pageDefinition.component)
    : false

  const pageTitle = pageDefinition?.title || pageSlug || 'Custom Page'

  // Loading state
  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  // Error state
  if (error) {
    return (
      <div className="space-y-4 p-6">
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error</AlertTitle>
          <AlertDescription>
            {error instanceof Error ? error.message : 'Failed to load page.'}
          </AlertDescription>
        </Alert>
      </div>
    )
  }

  // Page not found
  if (!pageDefinition) {
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
            <FileQuestion className="h-12 w-12 text-muted-foreground" />
            <div className="space-y-2">
              <h2 className="text-lg font-semibold text-foreground">Page Not Found</h2>
              <p className="text-sm text-muted-foreground">
                The page &quot;{pageSlug}&quot; is not configured. Check the page definition in
                Setup or contact your administrator.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Component not registered
  if (!hasComponent) {
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
              <BreadcrumbPage>{pageTitle}</BreadcrumbPage>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
        <Card>
          <CardContent className="flex flex-col items-center gap-4 py-12 text-center">
            <FileQuestion className="h-12 w-12 text-muted-foreground" />
            <div className="space-y-2">
              <h2 className="text-lg font-semibold text-foreground">Component Not Available</h2>
              <p className="text-sm text-muted-foreground">
                The component &quot;{pageDefinition.component}&quot; required by this page is not
                registered. It may need to be installed via a plugin.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Render the custom page component
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
            <BreadcrumbPage>{pageTitle}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <ResolvedPageComponent
        componentName={pageDefinition.component}
        config={pageDefinition.props}
        tenantSlug={tenantSlug || ''}
        collectionName={pageDefinition.collectionName}
      />
    </div>
  )
}
