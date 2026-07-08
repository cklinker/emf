import { Columns3, ChevronDown, ChevronUp } from 'lucide-react'
import { Button } from '../ui/button'
import { Checkbox } from '../ui/checkbox'
import { Popover, PopoverContent, PopoverTrigger } from '../ui/popover'
import { useI18n } from '../../context/I18nContext'

export interface ColumnChooserField {
  name: string
  displayName?: string
}

export interface ColumnChooserProps {
  /** All choosable (accessible, non-system) fields in schema order. */
  fields: ColumnChooserField[]
  /** Currently visible column names, in display order. */
  visibleColumns: string[]
  /** New ordered visible-column list on any toggle/move. */
  onChange: (visibleColumns: string[]) => void
}

/**
 * Column visibility + order picker for the list view (app-data-entry slice 2).
 * Checked columns render in the listed order; up/down moves reorder. The result
 * feeds the active view's `visibleColumns` (URL-independent, persisted on save).
 */
export function ColumnChooser({ fields, visibleColumns, onChange }: ColumnChooserProps) {
  const { t } = useI18n()
  const visibleSet = new Set(visibleColumns)
  // Visible columns first (view order), then the remaining fields in schema order.
  const ordered = [
    ...visibleColumns
      .map((name) => fields.find((f) => f.name === name))
      .filter((f): f is ColumnChooserField => !!f),
    ...fields.filter((f) => !visibleSet.has(f.name)),
  ]

  const toggle = (name: string) => {
    if (visibleSet.has(name)) {
      if (visibleColumns.length <= 1) return // never zero columns
      onChange(visibleColumns.filter((c) => c !== name))
    } else {
      onChange([...visibleColumns, name])
    }
  }

  const move = (name: string, delta: -1 | 1) => {
    const index = visibleColumns.indexOf(name)
    const target = index + delta
    if (index === -1 || target < 0 || target >= visibleColumns.length) return
    const next = [...visibleColumns]
    next[index] = next[target]
    next[target] = name
    onChange(next)
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" data-testid="column-chooser-trigger">
          <Columns3 className="mr-1.5 h-4 w-4" aria-hidden />
          {t('columnChooser.trigger', 'Columns')}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-72 p-2 max-h-96 overflow-y-auto">
        <p className="px-2 pb-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
          {t('columnChooser.heading', 'Visible columns')}
        </p>
        {ordered.map((field) => {
          const checked = visibleSet.has(field.name)
          const position = visibleColumns.indexOf(field.name)
          return (
            <div
              key={field.name}
              className="flex items-center gap-2 rounded px-2 py-1.5 hover:bg-accent"
              data-testid={`column-row-${field.name}`}
            >
              <Checkbox
                checked={checked}
                onCheckedChange={() => toggle(field.name)}
                aria-label={`Toggle column ${field.displayName || field.name}`}
              />
              <span className="flex-1 truncate text-sm">{field.displayName || field.name}</span>
              {checked && (
                <span className="flex shrink-0 gap-0.5">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-6 w-6"
                    disabled={position <= 0}
                    onClick={() => move(field.name, -1)}
                    aria-label={`Move ${field.displayName || field.name} up`}
                    data-testid={`column-up-${field.name}`}
                  >
                    <ChevronUp className="h-3.5 w-3.5" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-6 w-6"
                    disabled={position === visibleColumns.length - 1}
                    onClick={() => move(field.name, 1)}
                    aria-label={`Move ${field.displayName || field.name} down`}
                    data-testid={`column-down-${field.name}`}
                  >
                    <ChevronDown className="h-3.5 w-3.5" />
                  </Button>
                </span>
              )}
            </div>
          )
        })}
      </PopoverContent>
    </Popover>
  )
}
