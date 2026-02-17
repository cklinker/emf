/**
 * ListViewToolbar Component
 *
 * Toolbar for the ObjectListPage with:
 * - Collection title
 * - "New" button to create records
 * - Actions dropdown (bulk delete, export CSV/JSON)
 * - Bulk selection indicator bar
 */

import React from 'react'
import { Plus, Download, Trash2, ChevronDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

export interface ListViewToolbarProps {
  /** Collection display name */
  collectionLabel: string
  /** Number of selected rows */
  selectedCount: number
  /** Total record count */
  totalCount: number
  /** Callback to create a new record */
  onNew: () => void
  /** Callback for bulk delete */
  onBulkDelete?: () => void
  /** Callback for CSV export */
  onExportCsv?: () => void
  /** Callback for JSON export */
  onExportJson?: () => void
  /** Callback to clear selection */
  onClearSelection?: () => void
}

export function ListViewToolbar({
  collectionLabel,
  selectedCount,
  totalCount,
  onNew,
  onBulkDelete,
  onExportCsv,
  onExportJson,
  onClearSelection,
}: ListViewToolbarProps): React.ReactElement {
  return (
    <div className="space-y-2">
      {/* Main toolbar row */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight text-foreground">
          {collectionLabel}
          {totalCount > 0 && (
            <span className="ml-2 text-sm font-normal text-muted-foreground">({totalCount})</span>
          )}
        </h1>

        <div className="flex items-center gap-2">
          {/* Actions dropdown */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm">
                Actions
                <ChevronDown className="ml-1.5 h-3.5 w-3.5" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {onExportCsv && (
                <DropdownMenuItem onClick={onExportCsv}>
                  <Download className="mr-2 h-4 w-4" />
                  Export as CSV
                </DropdownMenuItem>
              )}
              {onExportJson && (
                <DropdownMenuItem onClick={onExportJson}>
                  <Download className="mr-2 h-4 w-4" />
                  Export as JSON
                </DropdownMenuItem>
              )}
              {(onExportCsv || onExportJson) && onBulkDelete && <DropdownMenuSeparator />}
              {onBulkDelete && selectedCount > 0 && (
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  onClick={onBulkDelete}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete selected ({selectedCount})
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>

          {/* New record button */}
          <Button size="sm" onClick={onNew} aria-label={`New ${collectionLabel}`}>
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            New
          </Button>
        </div>
      </div>

      {/* Bulk selection bar */}
      {selectedCount > 0 && (
        <div className="flex items-center gap-3 rounded-md border border-primary/20 bg-primary/5 px-4 py-2 text-sm">
          <span className="font-medium">
            {selectedCount} record{selectedCount !== 1 ? 's' : ''} selected
          </span>
          {onBulkDelete && (
            <Button
              variant="ghost"
              size="sm"
              className="h-7 text-destructive hover:text-destructive"
              onClick={onBulkDelete}
            >
              <Trash2 className="mr-1.5 h-3.5 w-3.5" />
              Delete
            </Button>
          )}
          {onClearSelection && (
            <Button variant="ghost" size="sm" className="ml-auto h-7" onClick={onClearSelection}>
              Clear selection
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
