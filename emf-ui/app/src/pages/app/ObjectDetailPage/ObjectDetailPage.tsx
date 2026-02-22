/**
 * ObjectDetailPage
 *
 * Displays the detail view of a single record with:
 * - Breadcrumb navigation
 * - Record header with name, collection badge, and action buttons
 * - Quick actions menu for server-side scripts and custom operations
 * - Highlights panel showing key fields
 * - Detail sections organized in a 2-column field grid
 * - Related lists for master-detail child records
 * - Delete confirmation dialog
 */

import React, { useState, useCallback, useMemo, useEffect } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
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
import { useCollectionPermissions } from '@/hooks/useCollectionPermissions'
import { FieldRenderer } from '@/components/FieldRenderer'
import { DetailSection } from '@/components/DetailSection'
import { RelatedList } from '@/components/RelatedList'
import { LayoutRelatedLists } from '@/components/LayoutRelatedLists'
import { InsufficientPrivileges } from '@/components/InsufficientPrivileges'
import { NotesSection } from '@/components/NotesSection/NotesSection'
import { AttachmentsSection } from '@/components/AttachmentsSection/AttachmentsSection'
import { QuickActionsMenu } from '@/components/QuickActions'
import { useAnnounce } from '@/components/LiveRegion'
import { useAppContext } from '@/context/AppContext'
import { useAuth } from '@/context/AuthContext'
import { useApi } from '@/context/ApiContext'
import { usePageLayout } from '@/hooks/usePageLayout'
import { useLookupDisplayMap } from '@/hooks/useLookupDisplayMap'
import { useRecordContext } from '@/hooks/useRecordContext'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { QuickActionExecutionContext } from '@/types/quickActions'

/**
 * Represents a relationship where another collection has a field
 * that references this collection (reverse relationship).
 */
interface RelatedCollection {
  /** Related collection name */
  collectionName: string
  /** Field on the related collection that points to this record */
  foreignKeyField: string
}

/** Collection schema summary for reverse-relationship discovery */
interface CollectionWithFields {
  id: string
  name: string
  displayName: string
  fields: {
    id: string
    name: string
    displayName?: string
    type: string
    referenceTarget?: string
  }[]
}

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
  const { user } = useAuth()
  const { apiClient } = useApi()
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

  // Resolve page layout for this collection (returns null if none configured)
  const { layout, isLoading: layoutLoading } = usePageLayout(schema?.id, user?.id)

  // Resolve display labels for reference/lookup/master_detail fields
  const { lookupDisplayMap } = useLookupDisplayMap(fields)

  // Fetch record
  const {
    record,
    isLoading: recordLoading,
    error: recordError,
  } = useRecord({
    collectionName,
    recordId,
  })

  // Fetch permissions (combined object + field in one call)
  const {
    permissions,
    isFieldVisible,
    isLoading: permissionsLoading,
  } = useCollectionPermissions(collectionName)

  // Fetch notes and attachments in a single API call
  const {
    notes,
    attachments,
    invalidate: invalidateRecordContext,
  } = useRecordContext(schema?.id, recordId)

  // Screen reader announcements
  const { announce } = useAnnounce()

  // Mutations
  const mutations = useRecordMutation({
    collectionName: collectionName || '',
    onSuccess: () => {
      setShowDeleteDialog(false)
      announce('Record deleted successfully')
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

  // Announce when record finishes loading for screen readers
  useEffect(() => {
    if (record && recordTitle && recordTitle !== 'Loading...') {
      announce(`Viewing ${collectionLabel} record: ${recordTitle}`)
    }
    // Only announce on initial load, not re-renders
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [record?.id])

  // Split fields into highlights and detail sections (filtered by field permissions)
  const userFields = useMemo(() => {
    return fields
      .filter((f) => !SYSTEM_FIELDS.has(f.name) && f.name !== 'id')
      .filter((f) => isFieldVisible(f.name))
  }, [fields, isFieldVisible])

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

  // Discover reverse relationships (fallback when no layout is configured).
  // Fetches all collections and finds those with master_detail fields pointing
  // to the current collection, so we show child records (e.g., Order Items on Orders).
  const hasLayoutRelatedLists = !!(layout && layout.relatedLists.length > 0)

  const { data: allCollections } = useQuery({
    queryKey: ['all-collections-metadata'],
    queryFn: () =>
      apiClient.get<{ content: CollectionWithFields[] }>('/control/collections?size=1000'),
    enabled: !!collectionName && !hasLayoutRelatedLists && !layoutLoading,
    staleTime: 5 * 60 * 1000, // 5 minutes — collection metadata rarely changes
  })

  const relatedCollections = useMemo<RelatedCollection[]>(() => {
    if (hasLayoutRelatedLists || !allCollections?.content || !collectionName) return []

    const related: RelatedCollection[] = []
    for (const coll of allCollections.content) {
      if (coll.name === collectionName) continue
      const collFields = Array.isArray(coll.fields) ? coll.fields : []
      for (const field of collFields) {
        if (
          (field.type === 'master_detail' ||
            field.type === 'MASTER_DETAIL' ||
            field.type === 'lookup' ||
            field.type === 'LOOKUP') &&
          field.referenceTarget === collectionName
        ) {
          related.push({
            collectionName: coll.name,
            foreignKeyField: field.name,
          })
        }
      }
    }
    return related
  }, [hasLayoutRelatedLists, allCollections, collectionName])

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

  // Quick action execution context
  const quickActionContext = useMemo<QuickActionExecutionContext>(
    () => ({
      collectionName: collectionName || '',
      recordId,
      record: record || undefined,
      tenantSlug: tenantSlug || '',
    }),
    [collectionName, recordId, record, tenantSlug]
  )

  const isLoading = schemaLoading || recordLoading || permissionsLoading || layoutLoading

  // Loading state
  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  // Permission check: user must have canRead
  if (!permissions.canRead) {
    return (
      <InsufficientPrivileges
        action="view"
        resource={`this ${collectionLabel} record`}
        backPath={`${basePath}/o/${collectionName}`}
      />
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
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="truncate text-xl font-semibold tracking-tight text-foreground">
              {recordTitle}
            </h1>
            {mutations.remove.isPending && (
              <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
            )}
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="secondary">{collectionLabel}</Badge>
            <span className="font-mono text-xs text-muted-foreground">{recordId}</span>
          </div>
        </div>

        <div className="flex flex-shrink-0 items-center gap-2">
          {/* Quick Actions menu (only shown when actions are configured) */}
          <QuickActionsMenu
            collectionName={collectionName || ''}
            context="record"
            executionContext={quickActionContext}
          />
          {permissions.canEdit && (
            <Button size="sm" variant="outline" onClick={handleEdit}>
              <Pencil className="mr-1.5 h-3.5 w-3.5" />
              Edit
            </Button>
          )}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button size="sm" variant="outline" aria-label="More actions">
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {permissions.canCreate && (
                <DropdownMenuItem onClick={handleClone}>
                  <Copy className="mr-2 h-4 w-4" />
                  Clone
                </DropdownMenuItem>
              )}
              {permissions.canCreate && permissions.canDelete && <DropdownMenuSeparator />}
              {permissions.canDelete && (
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  onClick={handleDelete}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Highlights Panel */}
      {highlightFields.length > 0 && record && (
        <Card>
          <CardContent className="py-4">
            <div className="grid grid-cols-2 gap-x-8 gap-y-3 md:grid-cols-4">
              {highlightFields.map((field) => {
                const value = record[field.name]
                const isRef =
                  field.type === 'master_detail' ||
                  field.type === 'lookup' ||
                  field.type === 'reference'
                const displayLabel =
                  isRef && lookupDisplayMap?.[field.name]
                    ? lookupDisplayMap[field.name][String(value)] || undefined
                    : undefined

                return (
                  <div key={field.name} className="space-y-1">
                    <dt className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                      {field.displayName || field.name}
                    </dt>
                    <dd className="text-sm font-medium">
                      <FieldRenderer
                        type={field.type}
                        value={value}
                        fieldName={field.name}
                        displayName={field.displayName || field.name}
                        tenantSlug={tenantSlug}
                        targetCollection={field.referenceTarget}
                        displayLabel={displayLabel}
                        truncate
                      />
                    </dd>
                  </div>
                )
              })}
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
          lookupDisplayMap={lookupDisplayMap}
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

      {/* Related Lists — use layout when available, otherwise auto-discover reverse relationships */}
      {recordId && hasLayoutRelatedLists ? (
        <div className="space-y-4">
          <Separator />
          <LayoutRelatedLists
            relatedLists={layout!.relatedLists}
            parentRecordId={recordId}
            tenantSlug={tenantSlug || ''}
          />
        </div>
      ) : relatedCollections.length > 0 && recordId ? (
        <div className="space-y-4">
          <Separator />
          {relatedCollections.map((rel) => (
            <RelatedList
              key={`${rel.collectionName}-${rel.foreignKeyField}`}
              collectionName={rel.collectionName}
              foreignKeyField={rel.foreignKeyField}
              parentRecordId={recordId}
              tenantSlug={tenantSlug || ''}
            />
          ))}
        </div>
      ) : null}

      {/* Notes & Attachments */}
      {schema && recordId && (
        <div className="space-y-4">
          <Separator />
          <NotesSection
            collectionId={schema.id}
            recordId={recordId}
            apiClient={apiClient}
            notes={notes}
            onMutate={invalidateRecordContext}
          />
          <AttachmentsSection
            collectionId={schema.id}
            recordId={recordId}
            apiClient={apiClient}
            attachments={attachments}
            onMutate={invalidateRecordContext}
          />
        </div>
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
