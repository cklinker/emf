/**
 * ObjectFormPage
 *
 * Create or edit a record using a layout-driven form.
 * Uses react-hook-form + zod for validation, shadcn Form components.
 *
 * Phase 4 will add: full form sections, field rendering, lookup search,
 * dependent picklists, multi-picklist, validation rules.
 */

import React from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { Save, X, FileEdit } from 'lucide-react'
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

export function ObjectFormPage(): React.ReactElement {
  const { tenantSlug, collection, id } = useParams<{
    tenantSlug: string
    collection: string
    id?: string
  }>()
  const navigate = useNavigate()
  const basePath = `/${tenantSlug}/app`

  const isNew = !id
  const collectionLabel = collection
    ? collection.charAt(0).toUpperCase() + collection.slice(1)
    : 'Object'

  const pageTitle = isNew ? `New ${collectionLabel}` : `Edit ${collectionLabel}`

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
            <BreadcrumbPage>{isNew ? 'New' : 'Edit'}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Form header */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight text-foreground">{pageTitle}</h1>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              if (id) {
                navigate(`${basePath}/o/${collection}/${id}`)
              } else {
                navigate(`${basePath}/o/${collection}`)
              }
            }}
          >
            <X className="mr-1.5 h-3.5 w-3.5" />
            Cancel
          </Button>
          <Button size="sm" disabled>
            <Save className="mr-1.5 h-3.5 w-3.5" />
            Save
          </Button>
        </div>
      </div>

      {/* Placeholder content */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FileEdit className="h-4 w-4 text-muted-foreground" />
            {pageTitle}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <FileEdit className="mb-4 h-12 w-12 text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground">
              Layout-driven form fields will be rendered here.
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              Phase 4 will add form sections, field inputs, validation, and CRUD operations.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
