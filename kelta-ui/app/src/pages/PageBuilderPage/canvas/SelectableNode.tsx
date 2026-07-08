/**
 * Wraps one canvas node (slice 2c): the selection outline + ring, a keyboard-accessible drag handle
 * (`useSortable` listeners — a real `<button>` so keyboard DnD works), the delete `×` button, the
 * "Custom" plugin badge, and — when the node is a child of a `grid`/`row` — a right-edge resize handle
 * that snaps `span.base` to the 12-col grid via `useSpanResize`. The node body is either a leaf preview
 * (the shared registry `renderNode`, editor mode) or, for a container node, the recursively-DnD-plumbed
 * `children` slot passed in by `CanvasContainer`. Preserves the legacy `canvas-component-{id}` /
 * `delete-component-{id}` testids the existing tests assert on.
 */
import React from 'react'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { GripVertical, EyeOff } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useI18n } from '../../../context/I18nContext'
import { componentRegistry } from '@/services/componentRegistry'
import { visibilityKind } from '../model/visibility'
import type { PageComponent, ResponsiveSpan } from '../model/pageModel'
import { widgetRegistry } from '../widgets/registry'
import { renderNode } from '../widgets/renderTree'
import { spanToClasses } from './spanClasses'
import { NODE_ID, type NodeDragData } from './dnd/types'
import { useSpanResize } from './dnd/useSpanResize'

export interface SelectableNodeProps {
  node: PageComponent
  parentId: string | null
  /** True when the parent is a `grid`/`row` — the node carries a `span` and shows a resize handle. */
  inGrid: boolean
  selectedId: string | null
  tenantSlug: string
  onSelect: (id: string) => void
  onDelete: (id: string) => void
  onSpanChange: (id: string, span: ResponsiveSpan) => void
  /** For a container node, the recursively-DnD-plumbed children slot (from `CanvasContainer`). */
  children?: React.ReactNode
}

export function SelectableNode({
  node,
  parentId,
  inGrid,
  selectedId,
  tenantSlug,
  onSelect,
  onDelete,
  onSpanChange,
  children,
}: SelectableNodeProps): React.ReactElement {
  const { t } = useI18n()

  const data: NodeDragData = { source: 'node', nodeId: node.id, parentId }
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: NODE_ID(node.id),
    data,
  })

  const isSelected = selectedId === node.id
  const isPlugin =
    !widgetRegistry.list().some((w) => w.type === node.type) &&
    componentRegistry.hasPageComponent(node.type)
  // Conditional visibility (app-platform slice 1): editor chrome from the RAW prop —
  // a literal false ghosts the node; a binding badges it (the editor scope can't
  // evaluate it). The node stays selectable/deletable either way.
  const nodeVisibility = visibilityKind(
    (node.props as Record<string, unknown> | undefined)?.visible
  )

  // The grid width is measured at pointer-down (inside the hook), not during render — no ref read here.
  const { handleProps } = useSpanResize({
    span: node.span,
    onCommit: (next) => onSpanChange(node.id, next),
  })

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : undefined,
  }

  // Container nodes render the DnD-plumbed children slot; leaf nodes render the descriptor preview.
  const body =
    children !== undefined ? children : renderNode(node, { mode: 'editor', tenantSlug, scope: {} })

  return (
    // eslint-disable-next-line jsx-a11y/no-static-element-interactions, jsx-a11y/click-events-have-key-events
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        'group relative rounded border border-border bg-muted/40 p-2 transition-[border-color,box-shadow]',
        inGrid && spanToClasses(node.span),
        isSelected && 'border-primary ring-2 ring-primary/20',
        isPlugin && 'border-blue-300 bg-blue-50 dark:border-blue-800 dark:bg-blue-950/40',
        nodeVisibility === 'literal-hidden' && 'opacity-50'
      )}
      // Selecting a node: clicking anywhere on the node chrome selects it (and stops the canvas
      // deselect). Keyboard select uses the drag-handle button + Enter via dnd-kit a11y.
      onClick={(e) => {
        e.stopPropagation()
        onSelect(node.id)
      }}
      data-testid={`canvas-component-${node.id}`}
      data-custom={isPlugin ? 'true' : 'false'}
    >
      <div className="mb-1 flex items-center justify-between gap-2">
        <div className="flex items-center gap-1">
          <button
            type="button"
            className="flex h-5 w-5 cursor-grab touch-none items-center justify-center rounded text-muted-foreground hover:bg-accent focus:outline-2 focus:outline-primary focus:outline-offset-2 active:cursor-grabbing"
            aria-label={t('builder.pages.reorderAria', { type: node.type })}
            data-testid={`drag-handle-${node.id}`}
            {...attributes}
            {...listeners}
          >
            <GripVertical size={12} />
          </button>
          <span className="text-xs font-medium uppercase text-muted-foreground">
            {node.type}
            {isPlugin && (
              <span
                className="ml-1 inline-flex items-center rounded bg-blue-100 px-1 py-[2px] text-[10px] font-medium uppercase tracking-wide text-blue-700 dark:bg-blue-900/60 dark:text-blue-300"
                data-testid={`custom-badge-${node.id}`}
              >
                Custom
              </span>
            )}
            {nodeVisibility !== 'default' && (
              <span
                className="ml-1 inline-flex items-center gap-0.5 rounded bg-muted px-1 py-[2px] text-[10px] font-medium uppercase tracking-wide text-muted-foreground"
                data-testid={`visibility-badge-${node.id}`}
              >
                <EyeOff className="h-2.5 w-2.5" aria-hidden />
                {nodeVisibility === 'bound'
                  ? t('builder.inspector.visibilityConditional')
                  : t('builder.inspector.visibilityHidden')}
              </span>
            )}
          </span>
        </div>
        <button
          type="button"
          className="flex h-5 w-5 items-center justify-center rounded text-base text-muted-foreground hover:bg-destructive/10 hover:text-destructive focus:outline-2 focus:outline-primary focus:outline-offset-2"
          onClick={(e) => {
            e.stopPropagation()
            onDelete(node.id)
          }}
          aria-label={`Delete ${node.type} component`}
          data-testid={`delete-component-${node.id}`}
        >
          ×
        </button>
      </div>

      <div className="text-sm text-foreground" data-testid={`node-body-${node.id}`}>
        {body}
      </div>

      {inGrid && (
        <button
          type="button"
          className="absolute right-0 top-1/2 h-8 w-2 -translate-y-1/2 cursor-ew-resize rounded-full bg-primary/40 opacity-0 transition-opacity focus:opacity-100 focus:outline-2 focus:outline-primary group-hover:opacity-100"
          aria-label={t('builder.pages.resizeAria')}
          data-testid={`resize-handle-${node.id}`}
          {...handleProps}
        />
      )}
    </div>
  )
}
