/**
 * ColumnPicker
 *
 * Popover with a checkbox list of columns, letting users toggle visibility.
 * Stores hidden-column ids in localStorage keyed by `tableId` so the
 * preference persists across reloads.
 */

import React, { useCallback, useEffect, useState } from 'react'
import { Columns } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { persistHiddenColumns } from './storage'

export interface ColumnPickerOption {
  id: string
  label: string
  /** Columns flagged required can't be hidden (e.g. row actions). */
  required?: boolean
}

export interface ColumnPickerProps {
  /** Stable id; used as the localStorage key. */
  tableId: string
  columns: ColumnPickerOption[]
  hidden: Set<string>
  onChange: (hidden: Set<string>) => void
}

export function ColumnPicker({
  tableId,
  columns,
  hidden,
  onChange,
}: ColumnPickerProps): React.ReactElement {
  const [open, setOpen] = useState(false)

  // Persist whenever the parent's hidden set changes.
  useEffect(() => {
    persistHiddenColumns(tableId, hidden)
  }, [tableId, hidden])

  const toggle = useCallback(
    (id: string) => {
      const next = new Set(hidden)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      onChange(next)
    },
    [hidden, onChange]
  )

  const visibleCount = columns.filter((c) => !hidden.has(c.id)).length

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid="column-picker-trigger"
          aria-label="Toggle column visibility"
        >
          <Columns className="mr-1 h-4 w-4" />
          Columns
          <span className="ml-1 text-xs text-muted-foreground">
            ({visibleCount}/{columns.length})
          </span>
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-56" data-testid="column-picker-content">
        <div className="flex flex-col gap-2">
          {columns.map((col) => {
            const checked = !hidden.has(col.id)
            return (
              <Label
                key={col.id}
                className="flex cursor-pointer items-center gap-2 text-sm font-normal"
                htmlFor={`column-toggle-${col.id}`}
              >
                <Checkbox
                  id={`column-toggle-${col.id}`}
                  checked={checked}
                  disabled={col.required}
                  onCheckedChange={() => toggle(col.id)}
                  data-testid={`column-toggle-${col.id}`}
                />
                <span className={col.required ? 'text-muted-foreground' : undefined}>
                  {col.label}
                </span>
              </Label>
            )
          })}
        </div>
      </PopoverContent>
    </Popover>
  )
}
