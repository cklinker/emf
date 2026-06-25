/**
 * The page-builder canvas (slice 2c) — replaces the legacy native-HTML5-DnD `Canvas`. One `DndContext`
 * (Pointer + Keyboard sensors, `closestCenter`, Kelta a11y announcements) wraps BOTH the palette (its
 * draggable tiles) and the canvas droppable tree, so a palette tile can be dragged into any container.
 * The tree renders through `CanvasContainer` (root level) → `SelectableNode` recursion; every container
 * is a droppable with its own `SortableContext`, and drops/moves/resizes route through `treeOps` via
 * `useCanvasDnd` / `onSpanChange`, handed back as a whole new tree through `onChange`. Preserves the
 * `page-canvas` testid + empty-state copy.
 */
import React, { useCallback } from 'react'
import { DndContext, DragOverlay, closestCenter } from '@dnd-kit/core'
import { useI18n } from '../../../context/I18nContext'
import type { PageComponent, ResponsiveSpan } from '../model/pageModel'
import { setSpan } from '../model/treeOps'
import { widgetRegistry } from '../widgets/registry'
import { CanvasContainer } from './CanvasContainer'
import { useCanvasDnd } from './dnd/useCanvasDnd'
import { buildAnnouncements, buildScreenReaderInstructions } from './dnd/announcements'

export interface CanvasProps {
  components: PageComponent[]
  selectedId: string | null
  onSelect: (id: string | null) => void
  /** Tree mutations (drop / move / resize) flow back through this one callback. */
  onChange: (next: PageComponent[]) => void
  onDelete: (id: string) => void
  tenantSlug: string
  /** The palette element — rendered inside the shared `DndContext` so its tiles are draggable sources. */
  palette: React.ReactNode
}

export function Canvas({
  components,
  selectedId,
  onSelect,
  onChange,
  onDelete,
  tenantSlug,
  palette,
}: CanvasProps): React.ReactElement {
  const { t } = useI18n()

  const onSpanChange = useCallback(
    (id: string, span: ResponsiveSpan) => onChange(setSpan(components, id, span)),
    [components, onChange]
  )

  const { sensors, onDragStart, onDragEnd, onDragCancel, activeDrag } = useCanvasDnd({
    tree: components,
    onChange,
    onSelectNew: onSelect,
  })

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onDragCancel={onDragCancel}
      accessibility={{
        announcements: buildAnnouncements(t),
        screenReaderInstructions: buildScreenReaderInstructions(t),
      }}
    >
      {palette}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions, jsx-a11y/click-events-have-key-events */}
      <div
        className="min-h-[400px] overflow-y-auto rounded-md border-2 border-dashed border-border bg-background p-4 max-md:min-h-[300px]"
        onClick={() => onSelect(null)}
        data-testid="page-canvas"
        role="region"
        aria-label="Page canvas"
      >
        {components.length === 0 && (
          <div className="pointer-events-none flex min-h-[260px] flex-col items-center justify-center text-center text-muted-foreground">
            <p>{t('builder.pages.canvasEmpty')}</p>
            <p className="mt-2 text-sm">{t('builder.pages.canvasHint')}</p>
          </div>
        )}
        {/* The root droppable always exists (even when empty) so a palette drop appends to root. */}
        <CanvasContainer
          node={null}
          children={components}
          selectedId={selectedId}
          tenantSlug={tenantSlug}
          onSelect={onSelect}
          onDelete={onDelete}
          onSpanChange={onSpanChange}
        />
      </div>

      <DragOverlay>
        {activeDrag ? (
          <div className="rounded border border-primary bg-background px-3 py-2 text-xs font-medium uppercase text-muted-foreground shadow-lg">
            {activeDrag.source === 'palette'
              ? widgetRegistry.get(activeDrag.widgetType ?? '').label
              : (activeDrag.nodeId ?? '')}
          </div>
        ) : null}
      </DragOverlay>
    </DndContext>
  )
}
