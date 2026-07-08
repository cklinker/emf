/**
 * ObjectListPage
 *
 * Displays a paginated, sortable, selectable list of records for a given collection.
 * Driven by the collection schema with field type-aware rendering.
 *
 * Features:
 * - Sortable column headers
 * - Row selection with bulk actions
 * - Field type-aware rendering (FieldRenderer)
 * - Pagination with page size control
 * - Row click navigation to record detail
 * - Row action menu (View, Edit, Delete)
 * - CSV/JSON export
 * - Quick actions menu for list-scoped operations
 * - Breadcrumb navigation
 */

import React, { useState, useCallback, useMemo, useEffect, useRef } from 'react'
import { useNavigate, useParams, useSearchParams, Link } from 'react-router-dom'
import { useCollectionStore } from '@/context/CollectionStoreContext'
import { Loader2, AlertCircle } from 'lucide-react'
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
import { Button } from '@/components/ui/button'
import { useCollectionSchema } from '@/hooks/useCollectionSchema'
import { useCollectionRecords } from '@/hooks/useCollectionRecords'
import { useRecordMutation } from '@/hooks/useRecordMutation'
import { useCollectionPermissions } from '@/hooks/useCollectionPermissions'
import { buildIncludedDisplayMap } from '@/utils/jsonapi'
import { REFERENCE_FIELD_TYPES, referenceIncludeParam } from './listIncludes'
import type { CollectionRecord } from '@/hooks/useCollectionRecords'
import { parseListViewParams } from './listUrlState'
import { ListShell } from '@/components/record/ListShell'
import { ObjectDataTable } from '@/components/ObjectDataTable/ObjectDataTable'
import { DataTablePagination } from '@/components/ObjectDataTable/DataTablePagination'
import { ListViewToolbar } from '@/components/ListViewToolbar'
import { CsvImportDialog } from '@/components/CsvImportDialog/CsvImportDialog'
import { FilterBar } from '@/components/FilterBar'
import { InsufficientPrivileges } from '@/components/InsufficientPrivileges'
import { QuickActionsMenu } from '@/components/QuickActions'
import { useAnnounce } from '@/components/LiveRegion'
import type { QuickActionExecutionContext } from '@/types/quickActions'

/**
 * Escape a value for CSV format.
 */
function escapeCSVValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  const str = typeof value === 'object' ? JSON.stringify(value) : String(value)
  if (str.includes(',') || str.includes('"') || str.includes('\n') || str.includes('\r')) {
    return `"${str.replace(/"/g, '""')}"`
  }
  return str
}

/**
 * Download a string as a file.
 */
function downloadFile(content: string, filename: string, mimeType: string): void {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

// URL state helpers (parseFilters/parseListViewParams) live in listUrlState.ts,
// shared with dashboard drill-through and saved views.

export function ObjectListPage(): React.ReactElement {
  const { tenantSlug, collection: collectionName } = useParams<{
    tenantSlug: string
    collection: string
  }>()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const collectionStore = useCollectionStore()
  const basePath = `/${tenantSlug}/app`

  // Parse list state from URL params (deep linking support)
  const { page, pageSize, sort, filters } = useMemo(
    () => parseListViewParams(searchParams),
    [searchParams]
  )

  // Selection state (local only — not persisted in URL)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  // Delete confirmation state
  const [deleteTarget, setDeleteTarget] = useState<CollectionRecord | null>(null)
  const [showBulkDeleteDialog, setShowBulkDeleteDialog] = useState(false)

  // Import dialog state
  const [showImportDialog, setShowImportDialog] = useState(false)

  // Fetch collection schema
  const {
    schema,
    fields,
    isLoading: schemaLoading,
    error: schemaError,
  } = useCollectionSchema(collectionName)

  // Fetch permissions (combined object + field in one call)
  const {
    permissions,
    isFieldVisible,
    isLoading: permissionsLoading,
  } = useCollectionPermissions(collectionName)

  // Determine visible fields (first 6 fields by default, excluding system and hidden fields)
  const visibleFields = useMemo(() => {
    if (!fields.length) return []
    return fields
      .filter((f) => !['createdAt', 'updatedAt', 'createdBy', 'updatedBy'].includes(f.name))
      .filter((f) => isFieldVisible(f.name))
      .slice(0, 6)
  }, [fields, isFieldVisible])

  // Identify reference fields in visible columns that need included resources
  const referenceFields = useMemo(
    () => visibleFields.filter((f) => REFERENCE_FIELD_TYPES.has(f.type) && f.referenceTarget),
    [visibleFields]
  )

  // Build the JSON:API `include` param from the reference FIELD names (see referenceIncludeParam).
  const includeParam = useMemo(() => referenceIncludeParam(referenceFields), [referenceFields])

  // Fetch records with includes for reference fields
  const {
    data: records,
    total,
    isLoading: recordsLoading,
    error: recordsError,
    refetch,
    rawResponse,
  } = useCollectionRecords({
    collectionName,
    page,
    pageSize,
    sort,
    filters: filters.length > 0 ? filters : undefined,
    enabled: !!schema,
    include: includeParam,
  })

  // Build lookup display map from included resources using centralized collection store
  const lookupDisplayMap = useMemo(() => {
    if (!rawResponse || referenceFields.length === 0) return undefined

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

    return Object.keys(map).length > 0 ? map : undefined
  }, [rawResponse, referenceFields, collectionStore])

  // Screen reader announcements for dynamic state changes
  const { announce } = useAnnounce()
  const prevRecordsLoadingRef = useRef(recordsLoading)

  useEffect(() => {
    // Announce when records finish loading
    if (prevRecordsLoadingRef.current && !recordsLoading) {
      if (recordsError) {
        announce('Error loading records', 'assertive')
      } else {
        announce(`Loaded ${records.length} of ${total} records`)
      }
    }
    prevRecordsLoadingRef.current = recordsLoading
  }, [recordsLoading, records.length, total, recordsError, announce])

  // Mutations
  const mutations = useRecordMutation({
    collectionName: collectionName || '',
    onSuccess: () => {
      setDeleteTarget(null)
      setShowBulkDeleteDialog(false)
      setSelectedIds(new Set())
      announce('Record deleted successfully')
    },
  })

  // In-place cell edit in the list grid (unified record experience, slice 3).
  const handleCellCommit = useCallback(
    async (recordId: string, fieldName: string, value: unknown): Promise<void> => {
      await mutations.patch.mutateAsync({ id: recordId, data: { [fieldName]: value } })
      await refetch()
    },
    [mutations.patch, refetch]
  )

  // Collection label
  const collectionLabel =
    schema?.displayName ||
    (collectionName ? collectionName.charAt(0).toUpperCase() + collectionName.slice(1) : 'Objects')

  /**
   * Update URL search params, preserving existing params and removing defaults.
   */
  const updateParams = useCallback(
    (updates: Record<string, string | undefined>) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          for (const [key, value] of Object.entries(updates)) {
            if (value === undefined) {
              next.delete(key)
            } else {
              next.set(key, value)
            }
          }
          // Remove defaults to keep URLs clean
          if (next.get('page') === '1') next.delete('page')
          if (next.get('pageSize') === '25') next.delete('pageSize')
          return next
        },
        { replace: true }
      )
    },
    [setSearchParams]
  )

  // Sort handler: cycle through asc → desc → none
  const handleSortChange = useCallback(
    (field: string) => {
      let newSort: string | undefined
      if (sort?.field === field) {
        newSort = sort.direction === 'asc' ? `-${field}` : undefined
      } else {
        newSort = field
      }
      updateParams({ sort: newSort, page: undefined })
    },
    [sort, updateParams]
  )

  // Page change handler
  const handlePageChange = useCallback(
    (newPage: number) => {
      updateParams({ page: newPage > 1 ? String(newPage) : undefined })
      setSelectedIds(new Set())
    },
    [updateParams]
  )

  // Page size change handler
  const handlePageSizeChange = useCallback(
    (newPageSize: number) => {
      updateParams({
        pageSize: newPageSize !== 25 ? String(newPageSize) : undefined,
        page: undefined,
      })
      setSelectedIds(new Set())
    },
    [updateParams]
  )

  // Navigation handlers
  const handleNew = useCallback(() => {
    navigate(`${basePath}/o/${collectionName}/new`)
  }, [navigate, basePath, collectionName])

  const handleEdit = useCallback(
    (record: CollectionRecord) => {
      navigate(`${basePath}/o/${collectionName}/${record.id}/edit`)
    },
    [navigate, basePath, collectionName]
  )

  // Delete handlers
  const handleDeleteClick = useCallback((record: CollectionRecord) => {
    setDeleteTarget(record)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (deleteTarget) {
      mutations.remove.mutate(deleteTarget.id)
    }
  }, [deleteTarget, mutations.remove])

  const handleBulkDeleteClick = useCallback(() => {
    if (selectedIds.size > 0) {
      setShowBulkDeleteDialog(true)
    }
  }, [selectedIds])

  const handleBulkDeleteConfirm = useCallback(() => {
    mutations.bulkDelete.mutate(Array.from(selectedIds))
  }, [mutations.bulkDelete, selectedIds])

  // Export handlers
  const handleExportCsv = useCallback(() => {
    if (!records.length || !visibleFields.length) return
    const exportRecords =
      selectedIds.size > 0 ? records.filter((r) => selectedIds.has(r.id)) : records
    const headers = visibleFields.map((f) => f.displayName || f.name)
    const rows = [headers.map(escapeCSVValue).join(',')]
    for (const record of exportRecords) {
      const values = visibleFields.map((f) => escapeCSVValue(record[f.name]))
      rows.push(values.join(','))
    }
    const timestamp = new Date().toISOString().slice(0, 10)
    downloadFile(rows.join('\n'), `${collectionName}-${timestamp}.csv`, 'text/csv;charset=utf-8')
  }, [records, visibleFields, selectedIds, collectionName])

  const handleExportJson = useCallback(() => {
    if (!records.length) return
    const exportRecords =
      selectedIds.size > 0 ? records.filter((r) => selectedIds.has(r.id)) : records
    const timestamp = new Date().toISOString().slice(0, 10)
    downloadFile(
      JSON.stringify(exportRecords, null, 2),
      `${collectionName}-${timestamp}.json`,
      'application/json'
    )
  }, [records, selectedIds, collectionName])

  const handleClearSelection = useCallback(() => {
    setSelectedIds(new Set())
  }, [])

  // Filter handlers
  const handleRemoveFilter = useCallback(
    (filterId: string) => {
      const remaining = filters.filter((f) => f.id !== filterId)
      updateParams({
        filter: remaining.length > 0 ? JSON.stringify(remaining) : undefined,
        page: undefined,
      })
    },
    [filters, updateParams]
  )

  const handleClearAllFilters = useCallback(() => {
    updateParams({ filter: undefined, page: undefined })
  }, [updateParams])

  // Quick action execution context for list-scoped actions
  const quickActionContext = useMemo<QuickActionExecutionContext>(
    () => ({
      collectionName: collectionName || '',
      selectedIds: Array.from(selectedIds),
      tenantSlug: tenantSlug || '',
    }),
    [collectionName, selectedIds, tenantSlug]
  )

  // Status branch (permission gate / schema error), rendered by the shell in
  // place of the list frame. Order matches the legacy early-returns.
  const statusSlot: React.ReactNode = !permissions.canRead ? (
    <InsufficientPrivileges
      action="view"
      resource={collectionLabel}
      backPath={`${basePath}/home`}
    />
  ) : schemaError ? (
    <div className="space-y-4 p-6">
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Error loading collection</AlertTitle>
        <AlertDescription>
          {schemaError.message || 'Failed to load collection schema.'}
        </AlertDescription>
      </Alert>
      <Button variant="outline" onClick={() => refetch()}>
        Retry
      </Button>
    </div>
  ) : null

  return (
    <ListShell
      variant="enduser"
      isLoading={schemaLoading || permissionsLoading}
      statusSlot={statusSlot}
      breadcrumb={
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
      }
      toolbar={
        <ListViewToolbar
          collectionLabel={collectionLabel}
          selectedCount={selectedIds.size}
          totalCount={total}
          onNew={handleNew}
          onBulkDelete={handleBulkDeleteClick}
          onExportCsv={handleExportCsv}
          onExportJson={handleExportJson}
          onImportCsv={permissions.canCreate ? () => setShowImportDialog(true) : undefined}
          onClearSelection={handleClearSelection}
          canCreate={permissions.canCreate}
          canDelete={permissions.canDelete}
          quickActionsSlot={
            <QuickActionsMenu
              collectionName={collectionName || ''}
              context="list"
              executionContext={quickActionContext}
            />
          }
        />
      }
      filters={
        <>
          {/* Active filters bar */}
          <FilterBar
            filters={filters}
            onRemoveFilter={handleRemoveFilter}
            onClearAll={handleClearAllFilters}
          />

          {/* Error state for records */}
          {recordsError && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Error loading records</AlertTitle>
              <AlertDescription>
                {recordsError.message || 'Failed to load records.'}
              </AlertDescription>
            </Alert>
          )}
        </>
      }
      table={
        <ObjectDataTable
          records={records}
          fields={visibleFields}
          sort={sort}
          onSortChange={handleSortChange}
          selectedIds={selectedIds}
          onSelectionChange={setSelectedIds}
          isLoading={recordsLoading}
          collectionName={collectionName || ''}
          onEdit={permissions.canEdit ? handleEdit : undefined}
          onDelete={permissions.canDelete ? handleDeleteClick : undefined}
          lookupDisplayMap={lookupDisplayMap}
          editable={permissions.canEdit}
          onCellCommit={handleCellCommit}
        />
      }
      pagination={
        !recordsLoading && records.length > 0 ? (
          <DataTablePagination
            page={page}
            pageSize={pageSize}
            total={total}
            selectedCount={selectedIds.size}
            onPageChange={handlePageChange}
            onPageSizeChange={handlePageSizeChange}
          />
        ) : undefined
      }
      dialogs={
        <>
          {/* CSV import dialog */}
          <CsvImportDialog
            open={showImportDialog}
            onOpenChange={setShowImportDialog}
            collectionName={collectionName || ''}
            onImported={refetch}
          />

          {/* Single delete confirmation */}
          <AlertDialog
            open={!!deleteTarget}
            onOpenChange={(open) => {
              if (!open) setDeleteTarget(null)
            }}
          >
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Delete record?</AlertDialogTitle>
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

          {/* Bulk delete confirmation */}
          <AlertDialog open={showBulkDeleteDialog} onOpenChange={setShowBulkDeleteDialog}>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Delete {selectedIds.size} records?</AlertDialogTitle>
                <AlertDialogDescription>
                  This will permanently delete {selectedIds.size} record
                  {selectedIds.size !== 1 ? 's' : ''}. This action cannot be undone.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Cancel</AlertDialogCancel>
                <AlertDialogAction
                  onClick={handleBulkDeleteConfirm}
                  className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                >
                  {mutations.bulkDelete.isPending ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : null}
                  Delete {selectedIds.size} records
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </>
      }
    />
  )
}
