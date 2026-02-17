/**
 * ObjectDetailPage
 *
 * Displays the detail view of a single record, driven by resolved page layouts.
 * Shows record header, highlights panel, detail sections, and related lists.
 *
 * Phase 3 will add: layout-driven sections, inline field editing, related lists.
 */

import React from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { Pencil, MoreHorizontal, FileText } from 'lucide-react'
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

export function ObjectDetailPage(): React.ReactElement {
  const { tenantSlug, collection, id } = useParams<{
    tenantSlug: string
    collection: string
    id: string
  }>()
  const navigate = useNavigate()
  const basePath = `/${tenantSlug}/app`

  const collectionLabel = collection
    ? collection.charAt(0).toUpperCase() + collection.slice(1)
    : 'Object'

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
            <BreadcrumbLink asChild>
              <Link to={`${basePath}/o/${collection}`}>{collectionLabel}</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{id}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Record header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-foreground">Record Detail</h1>
          <p className="text-sm text-muted-foreground">
            {collectionLabel} &middot; {id}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => navigate(`${basePath}/o/${collection}/${id}/edit`)}
          >
            <Pencil className="mr-1.5 h-3.5 w-3.5" />
            Edit
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button size="sm" variant="outline" aria-label="More actions">
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem>Clone</DropdownMenuItem>
              <DropdownMenuItem className="text-destructive">Delete</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Placeholder content */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FileText className="h-4 w-4 text-muted-foreground" />
            Record Details
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <FileText className="mb-4 h-12 w-12 text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground">
              Layout-driven record details will be rendered here.
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              Phase 3 will add field sections, highlights panel, and related lists.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
