/**
 * ObjectDetailPage
 *
 * Displays the detail view of a single record with:
 * - Breadcrumb navigation
 * - Record header with name, collection badge, and action buttons
 * - Highlights panel showing key fields
 * - Detail sections organized in a 2-column field grid
 * - Delete confirmation dialog
 */

import React, { useState, useCallback, useMemo, useEffect } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { Loader2, AlertCircle, Pencil, Copy, Trash2, MoreHorizontal } from 'lucide-react'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useCollectionSchema } from '@/hooks/useCollectionSchema'
import { useRecord } from '@/hooks/useRecord'
import { useRecordMutation } from '@/hooks/useRecordMutation'
import { FieldRenderer } from '@/components/FieldRenderer'
import { DetailSection } from '@/components/DetailSection'
import { useAppContext } from '@/context/AppContext'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'

/** System fields to exclude from detail sections */
const SYSTEM_FIELDS = new Set([
  'createdAt',
  'updatedAt',
  'createdBy',
  'updatedBy',
  'created_at',
  'updated_at',
  'created_by',
  'updated_by',
])

/** Max fields to show in the highlights panel */
const MAX_HIGHLIGHT_FIELDS = 4

/**
 * Determine the record's display name from its fields.
 */
function getRecordDisplayName(
  record: Record<string, unknown>,
  fields: FieldDefinition[],
  displayFieldName?: string
): string {
  // Try explicit display field
  if (displayFieldName && record[displayFieldName]) {
    return String(record[displayFieldName])
  }
  // Try "name" field
  const nameField = fields.find((f) => f.name.toLowerCase() === 'name')
  if (nameField && record[nameField.name]) {
    return String(record[nameField.name])
  }
  // Fall back to first string field
  const firstString = fields.find((f) => f.type === 'string')
  if (firstString && record[firstString.name]) {
    return String(record[firstString.name])
  }
  // Fall back to ID
  return String(record.id || 'Untitled')
}

export function ObjectDetailPage(): React.ReactElement {
  const {
    tenantSlug,
    collection: collectionName,
    id: recordId,
  } = useParams<{
    tenantSlug: string
    collection: string
    id: string
  }>()
  const navigate = useNavigate()
  const { addRecentItem } = useAppContext()
  const basePath = `/${tenantSlug}/app`

  // Delete confirmation state
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)

  // Fetch collection schema
  const {
    schema,
    fields,
    isLoading: schemaLoading,
    error: schemaError,
  } = useCollectionSchema(collectionName)

  // Fetch record
  const {
    record,
    isLoading: recordLoading,
    error: recordError,
  } = useRecord({
    collectionName,
    recordId,
  })

  // Mutations
  const mutations = useRecordMutation({
    collectionName: collectionName || '',
    onSuccess: () => {
      setShowDeleteDialog(false)
      navigate(`${basePath}/o/${collectionName}`)
    },
  })

  // Collection label
  const collectionLabel =
    schema?.displayName ||
    (collectionName ? collectionName.charAt(0).toUpperCase() + collectionName.slice(1) : 'Object')

  // Display name for the record
  const recordTitle = useMemo(() => {
    if (!record || !fields.length) return recordId || 'Loading...'
    return getRecordDisplayName(record, fields, schema?.displayFieldName)
  }, [record, fields, recordId, schema?.displayFieldName])

  // Track recently viewed record
  useEffect(() => {
    if (record && recordId && collectionName && recordTitle !== 'Loading...') {
      addRecentItem({
        id: recordId,
        collectionName,
        label: recordTitle,
      })
    }
  }, [record, recordId, collectionName, recordTitle, addRecentItem])

  // Split fields into highlights and detail sections
  const userFields = useMemo(() => {
    return fields.filter((f) => !SYSTEM_FIELDS.has(f.name) && f.name !== 'id')
  }, [fields])

  const highlightFields = useMemo(() => {
    return userFields.slice(0, MAX_HIGHLIGHT_FIELDS)
  }, [userFields])

  const detailFields = useMemo(() => {
    return userFields.slice(MAX_HIGHLIGHT_FIELDS)
  }, [userFields])

  // System fields for the system info section
  const systemFields = useMemo(() => {
    return fields.filter((f) => SYSTEM_FIELDS.has(f.name))
  }, [fields])

  // Handlers
  const handleEdit = useCallback(() => {
    navigate(`${basePath}/o/${collectionName}/${recordId}/edit`)
  }, [navigate, basePath, collectionName, recordId])

  const handleDelete = useCallback(() => {
    setShowDeleteDialog(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (recordId) {
      mutations.remove.mutate(recordId)
    }
  }, [recordId, mutations.remove])

  const handleClone = useCallback(() => {
    // Navigate to the new form — clone will be handled by pre-populating from URL
    navigate(`${basePath}/o/${collectionName}/new`)
  }, [navigate, basePath, collectionName])

  const isLoading = schemaLoading || recordLoading

  // Loading state
  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  // Error state
  if (schemaError || recordError) {
    const errorMsg = schemaError?.message || recordError?.message || 'Failed to load record.'
    return (
      <div className="space-y-4 p-6">
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error</AlertTitle>
          <AlertDescription>{errorMsg}</AlertDescription>
        </Alert>
        <Button variant="outline" onClick={() => navigate(`${basePath}/o/${collectionName}`)}>
          Back to list
        </Button>
      </div>
    )
  }

  if (!record) {
    return (
      <div className="space-y-4 p-6">
        <Alert>
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Not found</AlertTitle>
          <AlertDescription>Record not found.</AlertDescription>
        </Alert>
        <Button variant="outline" onClick={() => navigate(`${basePath}/o/${collectionName}`)}>
          Back to list
        </Button>
      </div>
    )
  }

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
              <Link to={`${basePath}/o/${collectionName}`}>{collectionLabel}</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{recordTitle}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Record Header */}
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-semibold tracking-tight text-foreground">{recordTitle}</h1>
            {mutations.remove.isPending && (
              <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
            )}
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="secondary">{collectionLabel}</Badge>
            <span className="font-mono text-xs text-muted-foreground">{recordId}</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" onClick={handleEdit}>
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
              <DropdownMenuItem onClick={handleClone}>
                <Copy className="mr-2 h-4 w-4" />
                Clone
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                className="text-destructive focus:text-destructive"
                onClick={handleDelete}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                Delete
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Highlights Panel */}
      {highlightFields.length > 0 && record && (
        <Card>
          <CardContent className="py-4">
            <div className="grid grid-cols-2 gap-x-8 gap-y-3 md:grid-cols-4">
              {highlightFields.map((field) => (
                <div key={field.name} className="space-y-1">
                  <dt className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                    {field.displayName || field.name}
                  </dt>
                  <dd className="text-sm font-medium">
                    <FieldRenderer
                      type={field.type}
                      value={record[field.name]}
                      fieldName={field.name}
                      displayName={field.displayName || field.name}
                      tenantSlug={tenantSlug}
                      targetCollection={field.referenceTarget}
                      truncate
                    />
                  </dd>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      <Separator />

      {/* Detail fields section */}
      {detailFields.length > 0 && (
        <DetailSection
          title="Details"
          fields={detailFields}
          record={record}
          tenantSlug={tenantSlug}
        />
      )}

      {/* System Information section */}
      {systemFields.length > 0 && (
        <DetailSection
          title="System Information"
          fields={systemFields}
          record={record}
          tenantSlug={tenantSlug}
          defaultCollapsed
        />
      )}

      {/* Delete confirmation */}
      <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete {recordTitle}?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete this record. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDeleteConfirm}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {mutations.remove.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
