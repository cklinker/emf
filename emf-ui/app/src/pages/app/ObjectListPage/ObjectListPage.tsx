/**
 * ObjectListPage
 *
 * Displays a list of records for a given collection.
 * Driven by the collection schema and list view configuration.
 *
 * Phase 2 will add: DataTable, inline editing, bulk actions, list view selector.
 */

import React from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { Plus, List } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'

export function ObjectListPage(): React.ReactElement {
  const { tenantSlug, collection } = useParams<{ tenantSlug: string; collection: string }>()
  const navigate = useNavigate()
  const basePath = `/${tenantSlug}/app`

  const collectionLabel = collection
    ? collection.charAt(0).toUpperCase() + collection.slice(1)
    : 'Objects'

  return (
    <div className="space-y-4 p-6">
      {/* Breadcrumb */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to={`${basePath}/home`}>Home</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{collectionLabel}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Toolbar */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight text-foreground">{collectionLabel}</h1>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            onClick={() => navigate(`${basePath}/o/${collection}/new`)}
            aria-label={`New ${collectionLabel}`}
          >
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            New
          </Button>
        </div>
      </div>

      {/* Placeholder content */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <List className="h-4 w-4 text-muted-foreground" />
            {collectionLabel} Records
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <List className="mb-4 h-12 w-12 text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground">
              The data table for <strong>{collection}</strong> records will be rendered here.
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              Phase 2 will add sorting, filtering, pagination, and inline editing.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
