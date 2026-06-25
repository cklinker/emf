/**
 * Schema-driven component palette. Renders from `widgetRegistry.listByCategory()`, one section per
 * non-empty category in declaration order (layout → content → data → input → navigation → chart). Each
 * tile keeps the legacy affordances: `draggable`, `onDragStart(type)`, click → `onAddComponent(type)`,
 * a parameterized aria-label, and `data-testid="palette-item-<type>"`. Icons/labels come from the
 * descriptor. Plugin components are NOT listed here — the palette lists built-in widgets only.
 */
import React from 'react'
import { useI18n } from '../../../context/I18nContext'
import { widgetRegistry } from '../widgets/registry'
import type { WidgetCategory } from '../widgets/types'

export interface PaletteProps {
  onDragStart: (componentType: string) => void
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

export function Palette({ onDragStart, onAddComponent }: PaletteProps): React.ReactElement {
  const { t } = useI18n()
  const byCategory = widgetRegistry.listByCategory()

  return (
    <div
      className="bg-background border border-border rounded-md p-4 overflow-y-auto"
      data-testid="component-palette"
    >
      <h3 className="m-0 mb-4 text-sm font-semibold text-foreground">
        {t('builder.pages.components')}
      </h3>
      <div className="flex flex-col gap-4">
        {CATEGORY_ORDER.map((category) => {
          const widgets = byCategory[category]
          if (!widgets || widgets.length === 0) return null
          return (
            <div key={category} className="flex flex-col gap-2">
              <h4
                className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
                data-testid={`palette-category-${category}`}
              >
                {t(`builder.palette.category.${category}`)}
              </h4>
              <div className="grid grid-cols-2 max-md:grid-cols-4 gap-2">
                {widgets.map((widget) => {
                  const Icon = widget.icon
                  return (
                    <button
                      key={widget.type}
                      type="button"
                      className="flex flex-col items-center justify-center p-2 bg-muted border border-border rounded cursor-grab transition-colors hover:bg-accent hover:border-muted-foreground/40 focus:outline-2 focus:outline-primary focus:outline-offset-2 active:cursor-grabbing"
                      draggable
                      onDragStart={() => onDragStart(widget.type)}
                      onClick={() => onAddComponent(widget.type)}
                      aria-label={t('builder.palette.addAria', { label: widget.label })}
                      data-testid={`palette-item-${widget.type}`}
                    >
                      <span className="mb-1 text-muted-foreground">
                        <Icon size={18} />
                      </span>
                      <span className="text-xs text-muted-foreground">{widget.label}</span>
                    </button>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
