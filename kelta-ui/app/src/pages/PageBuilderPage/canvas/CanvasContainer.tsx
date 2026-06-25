/**
 * The DnD plumbing around a container's children slot (slice 2c). For a parent (the root, or a
 * `grid`/`row`/`column`/`card`/`container` node) it registers a `useDroppable` and wraps the children in
 * a `SortableContext` (so they reorder within the parent and accept nodes moved between containers).
 * Each child is a `SelectableNode`; a child that itself accepts children recurses into a nested
 * `CanvasContainer`. The container's own visual shell (the 12-col grid track for `grid`/`row`, the stack
 * for `column`/`card`/`container`) is applied here so editor layout matches the runtime.
 */
import React from 'react'
import { useDroppable } from '@dnd-kit/core'
import { SortableContext, rectSortingStrategy } from '@dnd-kit/sortable'
import { cn } from '@/lib/utils'
import type { PageComponent, ResponsiveSpan } from '../model/pageModel'
import { widgetRegistry } from '../widgets/registry'
import { GRID_CONTAINER_CLASS } from './spanClasses'
import { SelectableNode } from './SelectableNode'
import { CONTAINER_ID, NODE_ID, type DropData } from './dnd/types'

/** Container types that lay their children on the 12-col grid track (children carry `span`). */
const GRID_CONTAINERS = new Set(['grid', 'row'])

export interface CanvasContainerProps {
  /** The container node, or `null` for the root canvas level. */
  node: PageComponent | null
  children: PageComponent[]
  selectedId: string | null
  tenantSlug: string
  onSelect: (id: string) => void
  onDelete: (id: string) => void
  onSpanChange: (id: string, span: ResponsiveSpan) => void
}

/** True if a node type accepts children (a container the canvas should make a droppable). */
function acceptsChildren(type: string): boolean {
  return widgetRegistry.get(type).acceptsChildren === true
}

export function CanvasContainer({
  node,
  children,
  selectedId,
  tenantSlug,
  onSelect,
  onDelete,
  onSpanChange,
}: CanvasContainerProps): React.ReactElement {
  const containerId = node?.id ?? null
  const isGridTrack = node ? GRID_CONTAINERS.has(node.type) : false

  const dropData: DropData = { container: containerId, index: children.length }
  const { setNodeRef, isOver } = useDroppable({ id: CONTAINER_ID(containerId), data: dropData })

  const childIds = children.map((c) => NODE_ID(c.id))

  const layoutClass = isGridTrack ? GRID_CONTAINER_CLASS : 'flex flex-col gap-2'

  return (
    <div
      ref={setNodeRef}
      className={cn(
        layoutClass,
        'min-h-[40px] rounded',
        isOver && 'bg-primary/5 outline-2 outline-dashed outline-primary/40'
      )}
      data-testid={node ? `container-${node.id}` : 'container-root'}
      data-grid-track={isGridTrack ? 'true' : undefined}
    >
      <SortableContext items={childIds} strategy={rectSortingStrategy}>
        {children.map((child) => {
          const childIsContainer = acceptsChildren(child.type)
          return (
            <SelectableNode
              key={child.id}
              node={child}
              parentId={containerId}
              inGrid={isGridTrack}
              selectedId={selectedId}
              tenantSlug={tenantSlug}
              onSelect={onSelect}
              onDelete={onDelete}
              onSpanChange={onSpanChange}
            >
              {childIsContainer ? (
                <CanvasContainer
                  node={child}
                  children={child.children ?? []}
                  selectedId={selectedId}
                  tenantSlug={tenantSlug}
                  onSelect={onSelect}
                  onDelete={onDelete}
                  onSpanChange={onSpanChange}
                />
              ) : undefined}
            </SelectableNode>
          )
        })}
      </SortableContext>
    </div>
  )
}
