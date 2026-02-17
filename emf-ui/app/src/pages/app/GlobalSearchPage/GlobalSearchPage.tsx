/**
 * GlobalSearchPage
 *
 * Full-page search results view. Displays results grouped by collection.
 * Phase 5 will add: full-text search, faceted filtering, result previews.
 */

import React from 'react'
import { useParams, useSearchParams, Link } from 'react-router-dom'
import { Search } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'

export function GlobalSearchPage(): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const [searchParams] = useSearchParams()
  const query = searchParams.get('q') || ''
  const basePath = `/${tenantSlug}/app`

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
            <BreadcrumbPage>Search</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <h1 className="text-xl font-semibold tracking-tight text-foreground">
        {query ? `Search results for "${query}"` : 'Search'}
      </h1>

      <Card>
        <CardContent className="py-12">
          <div className="flex flex-col items-center justify-center text-center">
            <Search className="mb-4 h-12 w-12 text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground">
              Full-page search results will be displayed here.
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              Phase 5 will add multi-collection search with filtering and previews.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
