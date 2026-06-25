/**
 * Layout built-ins: grid, row, column, container, card, divider. All but `divider` accept children and
 * render them through the shared `renderChild` path. `grid`/`row` lay children on a 12-col CSS-grid track
 * (each child wrapped in `spanToClasses(child.span)`); `column`/`container`/`card` stack children. The
 * single `Render` is used by BOTH the editor preview and the runtime, so `span` renders identically in
 * both (slice 2c). `mode` only changes the empty-state placeholder.
 */
import React from 'react'
import { Square, Box, LayoutGrid, Rows3, Columns3, Minus } from 'lucide-react'
import type { WidgetDescriptor, RenderNode, WidgetRenderProps } from '../types'
import { GRID_CONTAINER_CLASS, spanToClasses } from '../../canvas/spanClasses'

function renderStackedChildren({ node, renderChild }: WidgetRenderProps): React.ReactNode {
  return (node.children ?? []).map((child: RenderNode) => renderChild(child))
}

/** Render children on a 12-col grid track, wrapping each in its responsive span classes. */
function renderGridChildren({ node, renderChild }: WidgetRenderProps): React.ReactNode {
  return (node.children ?? []).map((child: RenderNode) => (
    <div key={child.id} className={spanToClasses(child.span)}>
      {renderChild(child)}
    </div>
  ))
}

const grid: WidgetDescriptor = {
  type: 'grid',
  label: 'Grid',
  icon: LayoutGrid,
  category: 'layout',
  defaultProps: {},
  // The grid itself has no editable props; children carry `span` (edited via the canvas resize handle / 2b).
  propSchema: [],
  acceptsChildren: true,
  Render: (props) => (
    <div className={GRID_CONTAINER_CLASS} data-testid="page-node-grid">
      {renderGridChildren(props)}
    </div>
  ),
}

const row: WidgetDescriptor = {
  type: 'row',
  label: 'Row',
  icon: Rows3,
  category: 'layout',
  defaultProps: {},
  propSchema: [],
  acceptsChildren: true,
  Render: (props) => (
    <div className={GRID_CONTAINER_CLASS} data-testid="page-node-row">
      {renderGridChildren(props)}
    </div>
  ),
}

const column: WidgetDescriptor = {
  type: 'column',
  label: 'Column',
  icon: Columns3,
  category: 'layout',
  defaultProps: {},
  propSchema: [{ key: 'span', label: 'Width', kind: 'span', group: 'layout' }],
  acceptsChildren: true,
  Render: (props) => {
    const kids = props.node.children ?? []
    if (kids.length === 0 && props.mode === 'editor') {
      return (
        <div
          className="min-h-[48px] rounded border border-dashed border-border"
          data-testid="page-node-column"
        />
      )
    }
    return (
      <div className="flex flex-col gap-4" data-testid="page-node-column">
        {renderStackedChildren(props)}
      </div>
    )
  },
}

const card: WidgetDescriptor = {
  type: 'card',
  label: 'Card',
  icon: Square,
  category: 'layout',
  defaultProps: {},
  propSchema: [
    { key: 'span', label: 'Column span', kind: 'span', group: 'layout' },
    { key: 'children', label: 'Children', kind: 'children', group: 'layout' },
  ],
  acceptsChildren: true,
  Render: (props) => (
    <div className="rounded-lg border border-border bg-card p-4" data-testid="page-node-card">
      {renderStackedChildren(props)}
    </div>
  ),
}

const container: WidgetDescriptor = {
  type: 'container',
  label: 'Container',
  icon: Box,
  category: 'layout',
  defaultProps: {},
  propSchema: [
    { key: 'span', label: 'Column span', kind: 'span', group: 'layout' },
    { key: 'children', label: 'Children', kind: 'children', group: 'layout' },
  ],
  acceptsChildren: true,
  Render: (props) => (
    <div className="space-y-4" data-testid="page-node-container">
      {renderStackedChildren(props)}
    </div>
  ),
}

const divider: WidgetDescriptor = {
  type: 'divider',
  label: 'Divider',
  icon: Minus,
  category: 'layout',
  defaultProps: {},
  propSchema: [],
  acceptsChildren: false,
  Render: () => <hr className="my-4 border-border" data-testid="page-node-divider" />,
}

export const layoutWidgets: WidgetDescriptor[] = [grid, row, column, container, card, divider]
