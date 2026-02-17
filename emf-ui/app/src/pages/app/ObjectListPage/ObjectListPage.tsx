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
 * - Breadcrumb navigation
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
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
import type { SortState, CollectionRecord } from '@/hooks/useCollectionRecords'
import { ObjectDataTable } from '@/components/ObjectDataTable/ObjectDataTable'
import { DataTablePagination } from '@/components/ObjectDataTable/DataTablePagination'
import { ListViewToolbar } from '@/components/ListViewToolbar'

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

export function ObjectListPage(): React.ReactElement {
  const { tenantSlug, collection: collectionName } = useParams<{
    tenantSlug: string
    collection: string
  }>()
  const navigate = useNavigate()
  const basePath = `/${tenantSlug}/app`

  // Pagination state
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(25)

  // Sort state
  const [sort, setSort] = useState<SortState | undefined>(undefined)

  // Selection state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  // Delete confirmation state
  const [deleteTarget, setDeleteTarget] = useState<CollectionRecord | null>(null)
  const [showBulkDeleteDialog, setShowBulkDeleteDialog] = useState(false)

  // Fetch collection schema
  const {
    schema,
    fields,
    isLoading: schemaLoading,
    error: schemaError,
  } = useCollectionSchema(collectionName)

  // Determine visible fields (first 6 fields by default, excluding system fields)
  const visibleFields = useMemo(() => {
    if (!fields.length) return []
    return fields
      .filter((f) => !['createdAt', 'updatedAt', 'createdBy', 'updatedBy'].includes(f.name))
      .slice(0, 6)
  }, [fields])

  // Fetch records
  const {
    data: records,
    total,
    isLoading: recordsLoading,
    error: recordsError,
    refetch,
  } = useCollectionRecords({
    collectionName,
    page,
    pageSize,
    sort,
    enabled: !!schema,
  })

  // Mutations
  const mutations = useRecordMutation({
    collectionName: collectionName || '',
    onSuccess: () => {
      setDeleteTarget(null)
      setShowBulkDeleteDialog(false)
      setSelectedIds(new Set())
    },
  })

  // Collection label
  const collectionLabel =
    schema?.displayName ||
    (collectionName ? collectionName.charAt(0).toUpperCase() + collectionName.slice(1) : 'Objects')

  // Sort handler: cycle through asc → desc → none
  const handleSortChange = useCallback((field: string) => {
    setSort((prev) => {
      if (prev?.field === field) {
        return prev.direction === 'asc' ? { field, direction: 'desc' } : undefined
      }
      return { field, direction: 'asc' }
    })
    setPage(1)
  }, [])

  // Page change handler
  const handlePageChange = useCallback((newPage: number) => {
    setPage(newPage)
    setSelectedIds(new Set())
  }, [])

  // Page size change handler
  const handlePageSizeChange = useCallback((newPageSize: number) => {
    setPageSize(newPageSize)
    setPage(1)
    setSelectedIds(new Set())
  }, [])

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

  // Loading state for schema
  if (schemaLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  // Error state
  if (schemaError) {
    return (
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
            <BreadcrumbPage>{collectionLabel}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Toolbar */}
      <ListViewToolbar
        collectionLabel={collectionLabel}
        selectedCount={selectedIds.size}
        totalCount={total}
        onNew={handleNew}
        onBulkDelete={handleBulkDeleteClick}
        onExportCsv={handleExportCsv}
        onExportJson={handleExportJson}
        onClearSelection={handleClearSelection}
      />

      {/* Error state for records */}
      {recordsError && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error loading records</AlertTitle>
          <AlertDescription>{recordsError.message || 'Failed to load records.'}</AlertDescription>
        </Alert>
      )}

      {/* Data table */}
      <ObjectDataTable
        records={records}
        fields={visibleFields}
        sort={sort}
        onSortChange={handleSortChange}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
        isLoading={recordsLoading}
        collectionName={collectionName || ''}
        onEdit={handleEdit}
        onDelete={handleDeleteClick}
      />

      {/* Pagination */}
      {!recordsLoading && records.length > 0 && (
        <DataTablePagination
          page={page}
          pageSize={pageSize}
          total={total}
          selectedCount={selectedIds.size}
          onPageChange={handlePageChange}
          onPageSizeChange={handlePageSizeChange}
        />
      )}

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
    </div>
  )
}
