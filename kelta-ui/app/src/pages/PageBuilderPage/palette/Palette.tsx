/**
 * Schema-driven component palette. Renders from `widgetRegistry.listByCategory()`, one section per
 * non-empty category in declaration order (layout → content → data → input → navigation → chart). Each
 * tile is a `@dnd-kit` draggable source (slice 2c) carrying `{ source:'palette', widgetType }` so it can
 * be dropped into any canvas container; it ALSO keeps the click-to-add path (`onAddComponent(type)`,
 * appends to root) and the `palette-item-<type>` testid + parameterized aria-label. The palette must be
 * rendered INSIDE the canvas `DndContext` (see `Canvas.tsx`). Plugin components are not listed here.
 */
import React from 'react'
import { useDraggable } from '@dnd-kit/core'
import { useI18n } from '../../../context/I18nContext'
import { widgetRegistry } from '../widgets/registry'
import type { WidgetCategory, WidgetDescriptor } from '../widgets/types'
import { PALETTE_ID, type PaletteDragData } from '../canvas/dnd/types'

export interface PaletteProps {
  onAddComponent: (componentType: string) => void
}

/** Category render order; empty categories are omitted. */
const CATEGORY_ORDER: WidgetCategory[] = [
  'layout',
  'content',
  'data',
  'input',
  'navigation',
  'chart',
]

interface PaletteTileProps {
  widget: WidgetDescriptor
  onAddComponent: (componentType: string) => void
}

/** One palette tile — a dnd-kit draggable source that also supports click-to-add. */
function PaletteTile({ widget, onAddComponent }: PaletteTileProps): React.ReactElement {
  const { t } = useI18n()
  const data: PaletteDragData = { source: 'palette', widgetType: widget.type }
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: PALETTE_ID(widget.type),
    data,
  })
  const Icon = widget.icon

  return (
    <button
      ref={setNodeRef}
      type="button"
      className="flex flex-col items-center justify-center rounded border border-border bg-muted p-2 transition-colors hover:border-muted-foreground/40 hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2 active:cursor-grabbing cursor-grab touch-none"
      style={{ opacity: isDragging ? 0.4 : undefined }}
      onClick={() => onAddComponent(widget.type)}
      aria-label={t('builder.palette.addAria', { label: widget.label })}
      data-testid={`palette-item-${widget.type}`}
      {...attributes}
      {...listeners}
    >
      <span className="mb-1 text-muted-foreground">
        <Icon size={18} />
      </span>
      <span className="text-xs text-muted-foreground">{widget.label}</span>
    </button>
  )
}

export function Palette({ onAddComponent }: PaletteProps): React.ReactElement {
  const { t } = useI18n()
  const byCategory = widgetRegistry.listByCategory()

  return (
    <div
      className="overflow-y-auto rounded-md border border-border bg-background p-4"
      data-testid="component-palette"
    >
      <h3 className="m-0 mb-4 text-sm font-semibold text-foreground">
        {t('builder.pages.components')}
      </h3>
      <div className="flex flex-col gap-4">
        {CATEGORY_ORDER.map((category) => {
          // Exclude palette-hidden internals (e.g. the `tab-panel` container — registered so the render
          // path resolves it, but never offered as a standalone, draggable palette widget).
          const widgets = (byCategory[category] ?? []).filter((w) => !w.paletteHidden)
          if (widgets.length === 0) return null
          return (
            <div key={category} className="flex flex-col gap-2">
              <h4
                className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
                data-testid={`palette-category-${category}`}
              >
                {t(`builder.palette.category.${category}`)}
              </h4>
              <div className="grid grid-cols-2 gap-2 max-md:grid-cols-4">
                {widgets.map((widget) => (
                  <PaletteTile key={widget.type} widget={widget} onAddComponent={onAddComponent} />
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
