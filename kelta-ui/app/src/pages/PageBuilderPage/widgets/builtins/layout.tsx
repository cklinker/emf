/** Layout built-ins: card, container. Both render their children through the shared path. */
import React from 'react'
import { Square, Box } from 'lucide-react'
import type { WidgetDescriptor, RenderNode, WidgetRenderProps } from '../types'

function renderChildren({ node, renderChild }: WidgetRenderProps): React.ReactNode {
  return (node.children ?? []).map((child: RenderNode) => renderChild(child))
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
      {renderChildren(props)}
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
      {renderChildren(props)}
    </div>
  ),
}

export const layoutWidgets: WidgetDescriptor[] = [card, container]
