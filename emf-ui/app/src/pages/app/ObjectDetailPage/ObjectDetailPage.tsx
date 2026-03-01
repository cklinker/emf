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
import { useCollectionStore } from '@/context/CollectionStoreContext'
import { useRecord } from '@/hooks/useRecord'
import { useRecordMutation } from '@/hooks/useRecordMutation'
import { useCollectionPermissions } from '@/hooks/useCollectionPermissions'
import { buildIncludedDisplayMap } from '@/utils/jsonapi'
import { FieldRenderer } from '@/components/FieldRenderer'
import { DetailSection } from '@/components/DetailSection'
import { RelatedList } from '@/components/RelatedList'
import { LayoutRelatedLists } from '@/components/LayoutRelatedLists'
import { LayoutFieldSections } from '@/components/LayoutFieldSections'
import { InsufficientPrivileges } from '@/components/InsufficientPrivileges'
import { NotesSection } from '@/components/NotesSection/NotesSection'
import { AttachmentsSection } from '@/components/AttachmentsSection/AttachmentsSection'
import { QuickActionsMenu } from '@/components/QuickActions'
import { useAnnounce } from '@/components/LiveRegion'
import { useAppContext } from '@/context/AppContext'
import { useAuth } from '@/context/AuthContext'
import { useApi } from '@/context/ApiContext'
import { usePageLayout } from '@/hooks/usePageLayout'
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

/**
 * Synthesized system field definitions for audit fields.
 * These fields exist as physical columns on every tenant table but are not
 * in the field table. We define them here so the System Information section
 * can render them with proper types and lookup resolution.
 */
const SYSTEM_FIELD_DEFINITIONS: FieldDefinition[] = [
  {
    id: '__sys_createdBy',
    name: 'created_by',
    displayName: 'Created By',
    type: 'lookup',
    required: false,
    referenceTarget: 'users',
  },
  {
    id: '__sys_createdAt',
    name: 'created_at',
    displayName: 'Created',
    type: 'datetime',
    required: false,
  },
  {
    id: '__sys_updatedBy',
    name: 'updated_by',
    displayName: 'Updated By',
    type: 'lookup',
    required: false,
    referenceTarget: 'users',
  },
  {
    id: '__sys_updatedAt',
    name: 'updated_at',
    displayName: 'Updated',
    type: 'datetime',
    required: false,
  },
]

/** Max fields to show in the highlights panel */
const MAX_HIGHLIGHT_FIELDS = 4

/** Reference field types that indicate a foreign key to another collection */
const REFERENCE_FIELD_TYPES = new Set(['master_detail', 'lookup', 'reference'])

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
  const collectionStore = useCollectionStore()
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

  // Identify reference fields that need included resources for display labels
  const referenceFields = useMemo(
    () => fields.filter((f) => REFERENCE_FIELD_TYPES.has(f.type) && f.referenceTarget),
    [fields]
  )

  // Build unified include param combining:
  // 1. Forward includes — reference fields on this collection (e.g., customers, discount_codes)
  // 2. Reverse includes — collections with FK fields pointing to this collection (e.g., order_items, payments)
  // 3. Nested includes — reference targets of reverse collections (e.g., products via order_items)
  // The worker resolves all three types in a single request. The gateway's IncludeResolutionFilter
  // only handles forward includes but preserves the worker's reverse/transitive includes.
  const includeParam = useMemo(() => {
    const includes = new Set<string>()

    // Forward includes from reference fields on this collection
    for (const f of referenceFields) {
      includes.add(f.referenceTarget!)
    }

    // Always include users for createdBy/updatedBy display name resolution
    includes.add('users')

    // Reverse includes + nested: collections referencing this collection
    if (collectionName && !collectionStore.isLoading) {
      for (const coll of collectionStore.collections) {
        if (coll.name === collectionName) continue
        for (const field of coll.fields) {
          if (
            (field.type === 'master_detail' || field.type === 'lookup') &&
            field.referenceTarget === collectionName
          ) {
            // Add the reverse collection itself (e.g., order_items)
            includes.add(coll.name)
            // Add nested reference targets so the worker resolves them transitively
            // (e.g., order_items.product → products)
            for (const nestedField of coll.fields) {
              if (
                REFERENCE_FIELD_TYPES.has(nestedField.type) &&
                nestedField.referenceTarget &&
                nestedField.referenceTarget !== collectionName
              ) {
                includes.add(nestedField.referenceTarget)
              }
            }
          }
        }
      }
    }

    return includes.size > 0 ? [...includes].join(',') : undefined
  }, [referenceFields, collectionName, collectionStore.collections, collectionStore.isLoading])

  // Fetch record with all includes — wait for both schema and collection store
  // so the include param is fully computed before the query fires.
  const {
    record,
    isLoading: recordLoading,
    error: recordError,
    rawResponse,
  } = useRecord({
    collectionName,
    recordId,
    include: includeParam,
    enabled: !schemaLoading && !collectionStore.isLoading,
  })

  // Build lookup display map from included resources using centralized collection store
  const lookupDisplayMap = useMemo(() => {
    if (!rawResponse) return undefined

    const map: Record<string, Record<string, string>> = {}

    referenceFields.forEach((field) => {
      const targetType = field.referenceTarget!
      const refSchema = collectionStore.getCollectionByName(targetType)
      const displayField = refSchema?.displayFieldName || 'name'

      const fieldMap = buildIncludedDisplayMap(rawResponse, targetType, displayField)
      if (Object.keys(fieldMap).length > 0) {
        map[field.name] = fieldMap
      }
    })

    // Build display map for system audit fields (created_by, updated_by → users)
    // Both camelCase (system collections) and snake_case (tenant collections) keys
    // are populated so the map works regardless of which naming convention the
    // backend uses for a given collection.
    const usersDisplayField =
      collectionStore.getCollectionByName('users')?.displayFieldName || 'email'
    const usersMap = buildIncludedDisplayMap(rawResponse, 'users', usersDisplayField)
    if (Object.keys(usersMap).length > 0) {
      map['createdBy'] = usersMap
      map['updatedBy'] = usersMap
      map['created_by'] = usersMap
      map['updated_by'] = usersMap
    }

    return Object.keys(map).length > 0 ? map : undefined
  }, [rawResponse, referenceFields, collectionStore])

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

  // System fields for the system info section.
  // For tenant collections, schema fields won't include audit fields (they're
  // not in the field table), so we always use the synthesized definitions.
  const systemFields = useMemo(() => {
    const fromSchema = fields.filter((f) => SYSTEM_FIELDS.has(f.name))
    return fromSchema.length > 0 ? fromSchema : SYSTEM_FIELD_DEFINITIONS
  }, [fields])

  // Discover reverse relationships (fallback when no layout is configured).
  // Uses the centralized collection store to find collections with master_detail
  // fields pointing to the current collection (e.g., Order Items on Orders).
  const hasLayoutSections = !!(layout && layout.sections && layout.sections.length > 0)
  const hasLayoutRelatedLists = !!(layout && layout.relatedLists.length > 0)

  const relatedCollections = useMemo<RelatedCollection[]>(() => {
    if (hasLayoutRelatedLists || !collectionName || collectionStore.isLoading) return []

    const related: RelatedCollection[] = []
    for (const coll of collectionStore.collections) {
      if (coll.name === collectionName) continue
      for (const field of coll.fields) {
        if (
          (field.type === 'master_detail' || field.type === 'lookup') &&
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
  }, [
    hasLayoutRelatedLists,
    collectionStore.collections,
    collectionStore.isLoading,
    collectionName,
  ])

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

      {/* Field Sections — use page layout sections when available, otherwise fall back to generic highlights + details */}
      {hasLayoutSections ? (
        <>
          <LayoutFieldSections
            sections={layout!.sections}
            schemaFields={fields}
            record={record}
            tenantSlug={tenantSlug}
            lookupDisplayMap={lookupDisplayMap}
          />
        </>
      ) : (
        <>
          {/* Highlights Panel (fallback) */}
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

          {/* Detail fields section (fallback) */}
          {detailFields.length > 0 && (
            <DetailSection
              title="Details"
              fields={detailFields}
              record={record}
              tenantSlug={tenantSlug}
              lookupDisplayMap={lookupDisplayMap}
            />
          )}
        </>
      )}

      {/* Related Lists — use layout when available, otherwise auto-discover reverse relationships */}
      {recordId && hasLayoutRelatedLists ? (
        <div className="space-y-4">
          <Separator />
          <LayoutRelatedLists
            relatedLists={layout!.relatedLists}
            parentRecordId={recordId}
            tenantSlug={tenantSlug || ''}
            includedData={rawResponse}
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
              includedData={rawResponse}
            />
          ))}
        </div>
      ) : null}

      {/* System Information section — after related lists, before notes */}
      {systemFields.length > 0 && record && (
        <div className="space-y-4">
          <Separator />
          <DetailSection
            title="System Information"
            fields={systemFields}
            record={record}
            tenantSlug={tenantSlug}
            lookupDisplayMap={lookupDisplayMap}
          />
        </div>
      )}

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
