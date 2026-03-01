/**
 * ObjectDataTable Component
 *
 * A data table for displaying collection records with:
 * - Sortable column headers
 * - Row selection with select-all
 * - Field type-aware rendering via FieldRenderer
 * - Row click navigation to record detail
 * - Row action menu (Edit, Delete)
 * - Keyboard navigation (Arrow keys, Enter, Space, Home, End, Escape)
 * - Virtual scrolling for large datasets (>100 rows)
 */

import React, { useCallback, useMemo, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useVirtualizer } from '@tanstack/react-virtual'
import { ArrowUpDown, ArrowUp, ArrowDown, MoreHorizontal, Eye, Pencil, Trash2 } from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { FieldRenderer } from '@/components/FieldRenderer'
import { useTableKeyboardNav } from '@/hooks/useTableKeyboardNav'
import { cn } from '@/lib/utils'
import type { FieldDefinition } from '@/hooks/useCollectionSchema'
import type { CollectionRecord, SortState } from '@/hooks/useCollectionRecords'

/** Enable virtual scrolling when row count exceeds this threshold */
const VIRTUAL_THRESHOLD = 100

/** Estimated row height in pixels for the virtualizer */
const ROW_HEIGHT_ESTIMATE = 40

/** Max visible height for the virtual scrollable area */
const VIRTUAL_MAX_HEIGHT = 600

export interface ObjectDataTableProps {
  /** Records to display */
  records: CollectionRecord[]
  /** Field definitions for visible columns */
  fields: FieldDefinition[]
  /** Current sort state */
  sort?: SortState
  /** Callback when a column header is clicked for sorting */
  onSortChange: (field: string) => void
  /** Set of selected record IDs */
  selectedIds: Set<string>
  /** Callback when selection changes */
  onSelectionChange: (ids: Set<string>) => void
  /** Whether the table is in a loading state */
  isLoading?: boolean
  /** Collection name for building URLs */
  collectionName: string
  /** Callback when edit is clicked on a row */
  onEdit?: (record: CollectionRecord) => void
  /** Callback when delete is clicked on a row */
  onDelete?: (record: CollectionRecord) => void
  /** Lookup display map: { fieldName: { recordId: displayLabel } } */
  lookupDisplayMap?: Record<string, Record<string, string>>
}

/**
 * Renders a sort indicator icon based on the current sort state.
 */
function SortIcon({ field, sort }: { field: string; sort?: SortState }) {
  if (!sort || sort.field !== field) {
    return <ArrowUpDown className="ml-1 h-3.5 w-3.5 text-muted-foreground/50" />
  }
  if (sort.direction === 'asc') {
    return <ArrowUp className="ml-1 h-3.5 w-3.5" />
  }
  return <ArrowDown className="ml-1 h-3.5 w-3.5" />
}

/**
 * Loading skeleton for the data table.
 */
function TableSkeleton({ columnCount }: { columnCount: number }) {
  return (
    <>
      {Array.from({ length: 5 }).map((_, rowIdx) => (
        <TableRow key={rowIdx}>
          <TableCell className="w-[40px]">
            <Skeleton className="h-4 w-4" />
          </TableCell>
          {Array.from({ length: columnCount }).map((_, colIdx) => (
            <TableCell key={colIdx}>
              <Skeleton className="h-4 w-[80%]" />
            </TableCell>
          ))}
          <TableCell className="w-[50px]">
            <Skeleton className="h-8 w-8" />
          </TableCell>
        </TableRow>
      ))}
    </>
  )
}

/**
 * Renders a single data row. Extracted to reduce duplication between
 * normal and virtualized rendering paths.
 */
function DataRow({
  record,
  fields,
  isSelected,
  rowProps,
  tenantSlug,
  lookupDisplayMap,
  onRowClick,
  onSelectRow,
  onEdit,
  onDelete,
}: {
  record: CollectionRecord
  fields: FieldDefinition[]
  isSelected: boolean
  rowProps: {
    tabIndex: number
    'aria-selected': boolean
    'data-focused': boolean
    onFocus: () => void
  }
  tenantSlug: string | undefined
  lookupDisplayMap?: Record<string, Record<string, string>>
  onRowClick: (record: CollectionRecord) => void
  onSelectRow: (id: string) => void
  onEdit?: (record: CollectionRecord) => void
  onDelete?: (record: CollectionRecord) => void
}) {
  return (
    <TableRow
      key={record.id}
      className={cn('cursor-pointer', rowProps['data-focused'] && 'ring-2 ring-inset ring-ring')}
      data-state={isSelected ? 'selected' : undefined}
      tabIndex={rowProps.tabIndex}
      aria-selected={rowProps['aria-selected']}
      onFocus={rowProps.onFocus}
      onClick={() => onRowClick(record)}
    >
      {/* Checkbox */}
      <TableCell className="w-[40px]" onClick={(e) => e.stopPropagation()}>
        <Checkbox
          checked={isSelected}
          onCheckedChange={() => onSelectRow(record.id)}
          aria-label={`Select row ${record.id}`}
        />
      </TableCell>

      {/* Data cells */}
      {fields.map((field) => {
        const fieldValue = record[field.name]
        const isLookup =
          field.type === 'master_detail' || field.type === 'lookup' || field.type === 'reference'
        const displayLabel =
          isLookup && lookupDisplayMap?.[field.name]
            ? lookupDisplayMap[field.name][String(fieldValue)] || undefined
            : undefined

        return (
          <TableCell key={field.name} className="max-w-[300px]">
            <FieldRenderer
              type={field.type}
              value={fieldValue}
              fieldName={field.name}
              displayName={field.displayName || field.name}
              tenantSlug={tenantSlug}
              targetCollection={field.referenceTarget}
              displayLabel={displayLabel}
              truncate
            />
          </TableCell>
        )
      })}

      {/* Row actions */}
      <TableCell className="w-[50px]" onClick={(e) => e.stopPropagation()}>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Row actions">
              <MoreHorizontal className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => onRowClick(record)}>
              <Eye className="mr-2 h-4 w-4" />
              View
            </DropdownMenuItem>
            {onEdit && (
              <DropdownMenuItem onClick={() => onEdit(record)}>
                <Pencil className="mr-2 h-4 w-4" />
                Edit
              </DropdownMenuItem>
            )}
            {onDelete && (
              <DropdownMenuItem
                className="text-destructive focus:text-destructive"
                onClick={() => onDelete(record)}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                Delete
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </TableCell>
    </TableRow>
  )
}

export function ObjectDataTable({
  records,
  fields,
  sort,
  onSortChange,
  selectedIds,
  onSelectionChange,
  isLoading = false,
  collectionName,
  onEdit,
  onDelete,
  lookupDisplayMap,
}: ObjectDataTableProps): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const navigate = useNavigate()
  const basePath = `/${tenantSlug}/app`

  // Ref for virtual scrolling container
  const scrollContainerRef = useRef<HTMLDivElement>(null)

  // Whether to use virtual scrolling
  const useVirtual = records.length > VIRTUAL_THRESHOLD

  // Keyboard navigation
  const { handleKeyDown, tableRef, getRowProps } = useTableKeyboardNav({
    rowCount: records.length,
    onRowActivate: (index) => {
      if (records[index]) {
        navigate(`${basePath}/o/${collectionName}/${records[index].id}`)
      }
    },
    onRowToggle: (index) => {
      if (records[index]) {
        handleSelectRow(records[index].id)
      }
    },
    enabled: !isLoading && records.length > 0,
  })

  // Virtual row virtualizer (only active when useVirtual is true)
  const virtualizer = useVirtualizer({
    count: useVirtual ? records.length : 0,
    getScrollElement: () => scrollContainerRef.current,
    estimateSize: () => ROW_HEIGHT_ESTIMATE,
    overscan: 10,
  })

  // Check if all visible records are selected
  const allSelected = useMemo(() => {
    if (records.length === 0) return false
    return records.every((r) => selectedIds.has(r.id))
  }, [records, selectedIds])

  // Some (but not all) selected
  const someSelected = useMemo(() => {
    if (records.length === 0) return false
    const hasAny = records.some((r) => selectedIds.has(r.id))
    return hasAny && !allSelected
  }, [records, selectedIds, allSelected])

  // Handle select-all toggle
  const handleSelectAll = useCallback(() => {
    if (allSelected) {
      const next = new Set(selectedIds)
      for (const record of records) {
        next.delete(record.id)
      }
      onSelectionChange(next)
    } else {
      const next = new Set(selectedIds)
      for (const record of records) {
        next.add(record.id)
      }
      onSelectionChange(next)
    }
  }, [allSelected, records, selectedIds, onSelectionChange])

  // Handle individual row selection
  const handleSelectRow = useCallback(
    (recordId: string) => {
      const next = new Set(selectedIds)
      if (next.has(recordId)) {
        next.delete(recordId)
      } else {
        next.add(recordId)
      }
      onSelectionChange(next)
    },
    [selectedIds, onSelectionChange]
  )

  // Navigate to record detail
  const handleRowClick = useCallback(
    (record: CollectionRecord) => {
      navigate(`${basePath}/o/${collectionName}/${record.id}`)
    },
    [navigate, basePath, collectionName]
  )

  // Get aria-sort value for a column
  const getAriaSort = useCallback(
    (field: string): 'ascending' | 'descending' | 'none' => {
      if (!sort || sort.field !== field) return 'none'
      return sort.direction === 'asc' ? 'ascending' : 'descending'
    },
    [sort]
  )

  // Shared table header
  const tableHeader = (
    <TableHeader>
      <TableRow>
        {/* Checkbox column */}
        <TableHead className="w-[40px]">
          <Checkbox
            checked={allSelected ? true : someSelected ? 'indeterminate' : false}
            onCheckedChange={handleSelectAll}
            aria-label={allSelected ? 'Deselect all rows' : 'Select all rows'}
          />
        </TableHead>

        {/* Data columns */}
        {fields.map((field) => (
          <TableHead
            key={field.name}
            className="cursor-pointer select-none whitespace-nowrap"
            onClick={() => onSortChange(field.name)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                onSortChange(field.name)
              }
            }}
            tabIndex={0}
            aria-sort={getAriaSort(field.name)}
            role="columnheader"
          >
            <div className="flex items-center">
              {field.displayName || field.name}
              <SortIcon field={field.name} sort={sort} />
            </div>
          </TableHead>
        ))}

        {/* Actions column */}
        <TableHead className="w-[50px]">
          <span className="sr-only">Actions</span>
        </TableHead>
      </TableRow>
    </TableHeader>
  )

  // Render table body content
  const renderBody = () => {
    if (isLoading) {
      return <TableSkeleton columnCount={fields.length} />
    }

    if (records.length === 0) {
      return (
        <TableRow>
          <TableCell colSpan={fields.length + 2} className="h-24 text-center text-muted-foreground">
            No records found.
          </TableCell>
        </TableRow>
      )
    }

    // Virtual scrolling path: render only visible rows with spacers
    if (useVirtual) {
      const virtualRows = virtualizer.getVirtualItems()
      const totalSize = virtualizer.getTotalSize()

      return (
        <>
          {/* Top spacer */}
          {virtualRows.length > 0 && virtualRows[0].start > 0 && (
            <tr>
              <td style={{ height: virtualRows[0].start, padding: 0 }} />
            </tr>
          )}

          {/* Visible rows */}
          {virtualRows.map((virtualRow) => {
            const record = records[virtualRow.index]
            const isSelected = selectedIds.has(record.id)
            const rowPropsForIndex = getRowProps(virtualRow.index)
            return (
              <DataRow
                key={record.id}
                record={record}
                fields={fields}
                isSelected={isSelected}
                rowProps={rowPropsForIndex}
                tenantSlug={tenantSlug}
                lookupDisplayMap={lookupDisplayMap}
                onRowClick={handleRowClick}
                onSelectRow={handleSelectRow}
                onEdit={onEdit}
                onDelete={onDelete}
              />
            )
          })}

          {/* Bottom spacer */}
          {virtualRows.length > 0 && virtualRows[virtualRows.length - 1].end < totalSize && (
            <tr>
              <td
                style={{
                  height: totalSize - virtualRows[virtualRows.length - 1].end,
                  padding: 0,
                }}
              />
            </tr>
          )}
        </>
      )
    }

    // Normal rendering path (< VIRTUAL_THRESHOLD rows)
    return records.map((record, index) => {
      const isSelected = selectedIds.has(record.id)
      const rowPropsForIndex = getRowProps(index)
      return (
        <DataRow
          key={record.id}
          record={record}
          fields={fields}
          isSelected={isSelected}
          rowProps={rowPropsForIndex}
          tenantSlug={tenantSlug}
          lookupDisplayMap={lookupDisplayMap}
          onRowClick={handleRowClick}
          onSelectRow={handleSelectRow}
          onEdit={onEdit}
          onDelete={onDelete}
        />
      )
    })
  }

  // For virtual scrolling, wrap in a scrollable container with fixed height
  if (useVirtual) {
    return (
      <div
        className="rounded-md border"
        ref={tableRef}
        onKeyDown={handleKeyDown}
        role="grid"
        tabIndex={0}
        aria-label={`${collectionName} records`}
      >
        <Table>{tableHeader}</Table>
        <div
          ref={scrollContainerRef}
          className="overflow-auto"
          style={{ maxHeight: VIRTUAL_MAX_HEIGHT }}
        >
          <Table>
            <TableBody>{renderBody()}</TableBody>
          </Table>
        </div>
      </div>
    )
  }

  // Standard rendering (no virtual scrolling)
  return (
    <div
      className="rounded-md border"
      ref={tableRef}
      onKeyDown={handleKeyDown}
      role="grid"
      tabIndex={0}
      aria-label={`${collectionName} records`}
    >
      <Table>
        {tableHeader}
        <TableBody>{renderBody()}</TableBody>
      </Table>
    </div>
  )
}
